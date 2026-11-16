package se.metacraft.museum_collection;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import se.metacraft.museum_collection.db.Database;
import se.metacraft.museum_collection.db.EventRepository;
import se.metacraft.museum_collection.db.EventRepository.PlayerInfo;
import se.metacraft.museum_collection.db.EventRepository.Standings;
import se.metacraft.museum_collection.db.EventRepository.SubmitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The event on one running server: what it knows about the players online, and every trip to the database.
 * All of its state belongs to the server thread. A query runs on {@link Database}'s threads and its result
 * comes back through {@link #ask}, which hands it to the server thread before anything here or in the game
 * is touched.
 */
public final class EventService {

	/** How many players the leaderboard ({@code /museum top}) holds. */
	public static final int LEADERBOARD_SIZE = 10;
	/** Ticks after a submission until the standings are read again, so a burst of them is one query. */
	private static final int REFRESH_AFTER_SUBMISSION_TICKS = 40;

	/** What is cached of a player who is online. {@code team} is null for one on no team. */
	public record PlayerState(@Nullable Team team, int items) {}

	private final MinecraftServer server;
	private final Map<UUID, PlayerState> players = new HashMap<>();
	private final Map<UUID, Long> lastInteraction = new HashMap<>();
	/** The items each online player has registered, once read: a repeat is turned away without asking the database. */
	private final Map<UUID, Set<String>> submitted = new HashMap<>();
	/** The items on their way to the database, by player: another click on the same one is dropped. */
	private final Map<UUID, Set<String>> pending = new HashMap<>();
	/** The players whose team pick is on its way to the database. */
	private final Set<UUID> joining = new HashSet<>();
	@Nullable
	private Database database;
	/** The settings the last failed start used: not tried again until they change. */
	@Nullable
	private MuseumConfig.Database failedStart;
	private Standings standings = Standings.EMPTY;
	private boolean refreshing;
	private int nextRefreshTick;

	EventService(MinecraftServer server) {
		this.server = server;
	}

	/** Whether the event is on and has a database to talk to. */
	public boolean isRunning() {
		return database != null;
	}

	public Standings standings() {
		return standings;
	}

	/** What is known of an online player; null until their row has been read. */
	@Nullable
	public PlayerState state(UUID uuid) {
		return players.get(uuid);
	}

	// Lifecycle

	void tick() {
		int tick = server.getTickCount();
		if (tick % 20 == 0) {
			followConfig();
		}
		if (database != null && !refreshing && tick >= nextRefreshTick) {
			refreshStandings();
		}
	}

	/** Starts, stops or restarts the database to match the config, which a {@code /reload} may have changed. */
	private void followConfig() {
		var config = MuseumConfig.get();
		if (database != null && (!config.enabled() || !database.config().equals(config.database()))) {
			stop(false);
		}
		if (database == null && config.enabled() && !config.database().equals(failedStart)) {
			start(config.database());
		}
		if (!config.enabled()) {
			failedStart = null;
		}
	}

	private void start(MuseumConfig.Database settings) {
		try {
			database = new Database(settings);
		} catch (RuntimeException e) {
			failedStart = settings;
			MuseumCollection.LOGGER.error("Could not set up the event database ({}); fix the database block of config/{}.json5 and /reload", settings, MuseumCollection.MODID, e);
			return;
		}
		failedStart = null;
		nextRefreshTick = 0;
		for (var player : server.getPlayerList().getPlayers()) {
			loadPlayer(player);
		}
	}

	void stop(boolean wait) {
		if (database == null) {
			return;
		}
		database.close(wait);
		database = null;
		players.clear();
		submitted.clear();
		pending.clear();
		joining.clear();
		standings = Standings.EMPTY;
		refreshing = false;
	}

	/**
	 * Runs a query off the server thread and gives its result, or the error it failed with, back on it.
	 * A result that comes back after the database was stopped or swapped is dropped.
	 */
	private <T> void ask(Database.Query<T> query, BiConsumer<T, Throwable> onServerThread) {
		var asked = database;
		if (asked == null) {
			return;
		}
		asked.submit(query).whenComplete((result, error) -> server.execute(() -> {
			if (database == asked) {
				onServerThread.accept(result, error);
			}
		}));
	}

	// Players

	void onJoin(ServerPlayer player) {
		loadPlayer(player);
	}

	void onLeave(ServerPlayer player) {
		var uuid = player.getUUID();
		players.remove(uuid);
		lastInteraction.remove(uuid);
		submitted.remove(uuid);
		pending.remove(uuid);
		joining.remove(uuid);
	}

	private void loadPlayer(ServerPlayer player) {
		var uuid = player.getUUID();
		String name = player.nameAndId().name();
		String skin = Skins.hash(player);
		record Loaded(Optional<PlayerInfo> info, Set<String> items) {}
		ask(repository -> new Loaded(repository.loadPlayer(uuid, name, skin), repository.submittedItems(uuid)), (loaded, error) -> {
			if (error != null) {
				MuseumCollection.LOGGER.warn("Could not load {}'s event data: {}", name, error.toString());
				return;
			}
			if (server.getPlayerList().getPlayer(uuid) != null) {
				remember(uuid, loaded.info());
				// Merged: an item registered while this was being read is in neither, or already here.
				submitted.computeIfAbsent(uuid, u -> new HashSet<>()).addAll(loaded.items());
			}
		});
	}

	private void remember(UUID uuid, Optional<PlayerInfo> info) {
		var state = info.map(i -> new PlayerState(i.team(), i.items())).orElse(new PlayerState(null, 0));
		players.put(uuid, state);
	}

	// Submitting

	/** A player right-clicked a collector with their main hand. */
	void interact(ServerPlayer player) {
		var config = MuseumConfig.get();
		var uuid = player.getUUID();
		long now = System.nanoTime();
		Long last = lastInteraction.get(uuid);
		if (last != null && now - last < config.interactionCooldownMs() * 1_000_000L) {
			return;
		}
		lastInteraction.put(uuid, now);

		if (!config.submissionsOpen()) {
			refuse(player, Component.literal("The collector isn't taking items right now."));
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			refuse(player, Component.literal("Hold the item you want to register in your main hand."));
			return;
		}
		var state = players.get(uuid);
		if (state != null && state.team() == null) {
			TeamSelect.open(player);
			return;
		}
		// Only the item's registry id counts: no name, enchantment, damage or any other component.
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Component itemName = new ItemStack(stack.getItem()).getItemName();
		var known = submitted.get(uuid);
		if (known != null && known.contains(itemId)) {
			refuse(player, Component.literal("You have already registered ").append(itemName).append("."));
			return;
		}
		// Different items go to the database side by side; a second click on one already on its way is dropped.
		if (database == null || !pending.computeIfAbsent(uuid, u -> new HashSet<>()).add(itemId)) {
			return;
		}
		String name = player.nameAndId().name();
		String skin = Skins.hash(player);
		ask(repository -> repository.submit(uuid, name, skin, itemId), (result, error) -> {
			var inFlight = pending.get(uuid);
			if (inFlight != null) {
				inFlight.remove(itemId);
			}
			var online = server.getPlayerList().getPlayer(uuid);
			if (error != null) {
				MuseumCollection.LOGGER.warn("Could not register {} for {}: {}", itemId, name, error.toString());
				if (online != null) {
					refuse(online, Component.literal("The event database can't be reached right now. Try again in a moment."));
				}
				return;
			}
			if (online != null && result.outcome() != EventRepository.Outcome.NO_TEAM) {
				submitted.computeIfAbsent(uuid, u -> new HashSet<>()).add(itemId);
			}
			submitted(uuid, name, online, itemName, result);
		});
	}

	private void submitted(UUID uuid, String name, @Nullable ServerPlayer player, Component itemName, SubmitResult result) {
		var config = MuseumConfig.get();
		if (player != null) {
			remember(uuid, Optional.ofNullable(result.team()).map(team -> new PlayerInfo(team, result.playerItems())));
		}
		switch (result.outcome()) {
			case NO_TEAM -> {
				// Their row hadn't been read yet when they clicked: they pick a team first.
				if (player != null) {
					TeamSelect.open(player);
				}
			}
			case ALREADY_SUBMITTED -> {
				if (player != null) {
					refuse(player, Component.literal("You have already registered ").append(itemName).append("."));
				}
			}
			case REGISTERED -> {
				nextRefreshTick = Math.min(nextRefreshTick, server.getTickCount() + REFRESH_AFTER_SUBMISSION_TICKS);
				// Each registered item raises the count by one, so it equals the threshold exactly once. Rewarded
				// even if the player left before the database answered: the command takes offline players.
				boolean rewarded = result.playerItems() == config.participationThreshold() && reward(name);
				if (player == null || result.team() == null) {
					return;
				}
				var team = result.team();
				// On the action bar, not in chat: a player registers dozens of these in a row.
				var message = Component.literal("Registered ").withStyle(ChatFormatting.GREEN).append(itemName.copy().withStyle(ChatFormatting.WHITE));
				if (result.firstForTeam()) {
					message.append(Component.literal(", new for ")).append(team.displayName()).append("!");
				} else {
					message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY)).append(team.displayName())
							.append(Component.literal(" already had it").withStyle(ChatFormatting.GRAY));
				}
				message.append(Component.literal("  |  You: " + result.playerItems() + "  |  ").withStyle(ChatFormatting.GRAY))
						.append(team.displayName()).append(Component.literal(": " + result.teamItems()).withStyle(ChatFormatting.GRAY));
				player.sendOverlayMessage(message);
				play(player, result.firstForTeam() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP, result.firstForTeam() ? 0.6f : 0.8f);

				if (rewarded) {
					player.sendSystemMessage(Component.literal("You have registered " + result.playerItems() + " items and earned the participation reward!")
							.withStyle(ChatFormatting.GOLD));
					play(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f);
				}
			}
		}
	}

	/**
	 * Runs {@code participation_reward_command} for a player, as the console, so it shows in the server log.
	 *
	 * @return whether a command was run
	 */
	private boolean reward(String name) {
		String command = MuseumConfig.get().participationRewardCommand().strip();
		if (command.isEmpty()) {
			return false;
		}
		// A name is spliced into a command line, so only a real Minecraft name is.
		if (!name.matches("[A-Za-z0-9_]{1,16}")) {
			MuseumCollection.LOGGER.warn("Not rewarding {}: not a name that can go in a command", name);
			return false;
		}
		command = command.replace("{player}", name);
		MuseumCollection.LOGGER.info("{} reached the participation threshold: running /{}", name, command);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		return true;
	}

	private static void refuse(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.RED));
		play(player, SoundEvents.VILLAGER_NO, 0.8f);
	}

	private static void play(ServerPlayer player, SoundEvent sound, float volume) {
		player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, 1f);
	}

	// Teams

	/**
	 * A player picked a team in the selection dialog. It only takes for a player on no team: the pick is
	 * theirs once, and after that only an admin moves them.
	 */
	void join(ServerPlayer player, Team team) {
		var uuid = player.getUUID();
		String name = player.nameAndId().name();
		if (database == null || !joining.add(uuid)) {
			return;
		}
		String skin = Skins.hash(player);
		ask(repository -> repository.joinTeam(uuid, name, skin, team), (info, error) -> {
			joining.remove(uuid);
			var online = server.getPlayerList().getPlayer(uuid);
			if (error != null) {
				MuseumCollection.LOGGER.warn("Could not put {} on team {}: {}", name, team.id(), error.toString());
				if (online != null) {
					refuse(online, Component.literal("The event database can't be reached right now. Try again in a moment."));
				}
				return;
			}
			if (online == null) {
				return;
			}
			remember(uuid, Optional.of(info));
			if (info.team() == team) {
				online.sendSystemMessage(Component.literal("You joined team ").append(team.displayName())
						.append(". Right-click a collector with an item to register it."));
				play(online, SoundEvents.PLAYER_LEVELUP, 0.6f);
			} else {
				online.sendSystemMessage(Component.literal("You are already on team ").append(info.team().displayName()).append("."));
			}
		});
	}

	/**
	 * Puts a player (online or not) on a team; what they have submitted moves with them.
	 *
	 * @param done called on the server thread with the items they have, or with the error
	 */
	public void setTeam(UUID uuid, String name, Team team, BiConsumer<Integer, Throwable> done) {
		// An offline player's skin isn't known here: null keeps the stored one.
		var player = server.getPlayerList().getPlayer(uuid);
		String skin = player == null ? null : Skins.hash(player);
		ask(repository -> repository.setTeam(uuid, name, skin, team), (items, error) -> {
			if (error == null) {
				nextRefreshTick = Math.min(nextRefreshTick, server.getTickCount() + REFRESH_AFTER_SUBMISSION_TICKS);
				var online = server.getPlayerList().getPlayer(uuid);
				if (online != null) {
					remember(uuid, Optional.of(new PlayerInfo(team, items)));
					online.sendSystemMessage(Component.literal("You are on team ").append(team.displayName()).append("."));
				}
			}
			done.accept(items, error);
		});
	}

	/** Looks a player (online or not) up in the database. */
	public void lookUp(UUID uuid, String name, BiConsumer<Optional<PlayerInfo>, Throwable> done) {
		ask(repository -> repository.loadPlayer(uuid, name, null), done);
	}

	// Standings

	/** Reads the standings again now, or as soon as the read already under way is back. */
	public void requestRefresh() {
		nextRefreshTick = 0;
	}

	private void refreshStandings() {
		var config = MuseumConfig.get();
		refreshing = true;
		nextRefreshTick = server.getTickCount() + config.standingsRefreshSeconds() * 20;
		ask(repository -> repository.standings(LEADERBOARD_SIZE), (result, error) -> {
			refreshing = false;
			if (error != null) {
				MuseumCollection.LOGGER.warn("Could not read the event standings: {}", error.toString());
				return;
			}
			standings = result;
		});
	}

}
