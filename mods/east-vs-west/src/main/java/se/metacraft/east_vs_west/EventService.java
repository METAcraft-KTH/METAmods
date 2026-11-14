package se.metacraft.east_vs_west;

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
import se.metacraft.east_vs_west.db.Database;
import se.metacraft.east_vs_west.db.EventRepository;
import se.metacraft.east_vs_west.db.EventRepository.PlayerInfo;
import se.metacraft.east_vs_west.db.EventRepository.PlayerScore;
import se.metacraft.east_vs_west.db.EventRepository.Standings;
import se.metacraft.east_vs_west.db.EventRepository.SubmitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The event on one running server: what it knows about the players online, and every trip to the database.
 * All of its state belongs to the server thread. A query runs on {@link Database}'s threads and its result
 * comes back through {@link #ask}, which hands it to the server thread before anything here or in the game
 * is touched.
 */
public final class EventService {

	/** How many players the leaderboard holds; the legendary hat goes to at most this many. */
	public static final int LEADERBOARD_SIZE = 10;
	/** Ticks after a submission until the standings are read again, so a burst of them is one query. */
	private static final int REFRESH_AFTER_SUBMISSION_TICKS = 40;

	/** What is cached of a player who is online. {@code team} is null for one on no team. */
	public record PlayerState(@Nullable Team team, int items) {}

	private final MinecraftServer server;
	private final Map<UUID, PlayerState> players = new HashMap<>();
	private final Map<UUID, Long> lastInteraction = new HashMap<>();
	private final Set<UUID> submitting = new HashSet<>();
	@Nullable
	private Database database;
	/** The settings the last failed start used: not tried again until they change. */
	@Nullable
	private EvwConfig.Database failedStart;
	private Standings standings = Standings.EMPTY;
	@Nullable
	private Set<UUID> legendary;
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
		var config = EvwConfig.get();
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

	private void start(EvwConfig.Database settings) {
		try {
			database = new Database(settings);
		} catch (RuntimeException e) {
			failedStart = settings;
			EastVsWest.LOGGER.error("Could not set up the event database ({}); fix the database block of config/{}.json5 and /reload", settings, EastVsWest.MODID, e);
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
		submitting.clear();
		standings = Standings.EMPTY;
		legendary = null;
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
		submitting.remove(uuid);
	}

	private void loadPlayer(ServerPlayer player) {
		var uuid = player.getUUID();
		String name = player.nameAndId().name();
		ask(repository -> repository.loadPlayer(uuid, name), (info, error) -> {
			if (error != null) {
				EastVsWest.LOGGER.warn("Could not load {}'s event data: {}", name, error.toString());
				return;
			}
			if (server.getPlayerList().getPlayer(uuid) != null) {
				remember(uuid, info);
			}
		});
	}

	private void remember(UUID uuid, Optional<PlayerInfo> info) {
		var state = info.map(i -> new PlayerState(i.team(), i.items())).orElse(new PlayerState(null, 0));
		players.put(uuid, state);
	}

	/** The hat a player has earned, or null: the legendary one for the top players, else their team's. */
	@Nullable
	public EvwConfig.Hat hatFor(ServerPlayer player) {
		if (database == null) {
			return null;
		}
		var config = EvwConfig.get();
		if (isLegendary(player.getUUID())) {
			return config.legendary().hat();
		}
		var state = players.get(player.getUUID());
		if (state != null && state.team() != null && state.items() >= config.participationThreshold()) {
			return config.team(state.team()).hat();
		}
		return null;
	}

	public boolean isLegendary(UUID uuid) {
		return legendary != null && legendary.contains(uuid) && EvwConfig.get().legendary().enabled();
	}

	// Submitting

	/** A player right-clicked a collector with their main hand. */
	void interact(ServerPlayer player) {
		var config = EvwConfig.get();
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
		// One at a time per player; a second click while the first is on its way is simply dropped.
		if (!submitting.add(uuid)) {
			return;
		}
		// Only the item's registry id counts: no name, enchantment, damage or any other component.
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Component itemName = new ItemStack(stack.getItem()).getItemName();
		String name = player.nameAndId().name();
		if (database == null) {
			submitting.remove(uuid);
			return;
		}
		ask(repository -> repository.submit(uuid, name, itemId), (result, error) -> {
			submitting.remove(uuid);
			var online = server.getPlayerList().getPlayer(uuid);
			if (error != null) {
				EastVsWest.LOGGER.warn("Could not register {} for {}: {}", itemId, name, error.toString());
				if (online != null) {
					refuse(online, Component.literal("The event database can't be reached right now. Try again in a moment."));
				}
				return;
			}
			submitted(uuid, online, itemName, result);
		});
	}

	private void submitted(UUID uuid, @Nullable ServerPlayer player, Component itemName, SubmitResult result) {
		var config = EvwConfig.get();
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
				if (player == null || result.team() == null) {
					return;
				}
				var team = result.team();
				var message = Component.literal("Registered ").withStyle(ChatFormatting.GREEN).append(itemName.copy().withStyle(ChatFormatting.WHITE));
				if (result.firstForTeam()) {
					message.append(Component.literal(", a new item for ")).append(team.displayName()).append("!");
				} else {
					message.append(Component.literal(". ")).append(team.displayName()).append(Component.literal(" already had it.").withStyle(ChatFormatting.GRAY));
				}
				player.sendSystemMessage(message);
				player.sendOverlayMessage(Component.literal("You: " + result.playerItems() + " items  |  ").append(team.displayName())
						.append(Component.literal(": " + result.teamItems() + " unique")));
				play(player, result.firstForTeam() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP, result.firstForTeam() ? 0.6f : 0.8f);

				// Each registered item raises the count by one, so it equals the threshold exactly once.
				if (result.playerItems() == config.participationThreshold()) {
					player.sendSystemMessage(Component.literal("You have unlocked your team's hat: ").withStyle(ChatFormatting.GOLD)
							.append(Component.literal(config.team(team).hat().name()).withStyle(team.color())));
					play(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f);
				}
			}
		}
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
		if (database == null || !submitting.add(uuid)) {
			return;
		}
		ask(repository -> repository.joinTeam(uuid, name, team), (info, error) -> {
			submitting.remove(uuid);
			var online = server.getPlayerList().getPlayer(uuid);
			if (error != null) {
				EastVsWest.LOGGER.warn("Could not put {} on team {}: {}", name, team.id(), error.toString());
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
		ask(repository -> repository.setTeam(uuid, name, team), (items, error) -> {
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
		ask(repository -> repository.loadPlayer(uuid, name), done);
	}

	// Standings

	/** Reads the standings again now, or as soon as the read already under way is back. */
	public void requestRefresh() {
		nextRefreshTick = 0;
	}

	private void refreshStandings() {
		var config = EvwConfig.get();
		refreshing = true;
		nextRefreshTick = server.getTickCount() + config.standingsRefreshSeconds() * 20;
		ask(repository -> repository.standings(LEADERBOARD_SIZE), (result, error) -> {
			refreshing = false;
			if (error != null) {
				EastVsWest.LOGGER.warn("Could not read the event standings: {}", error.toString());
				return;
			}
			standings = result;
			crown(result.top().stream().limit(EvwConfig.get().legendary().topCount()).map(PlayerScore::uuid).collect(Collectors.toUnmodifiableSet()));
		});
	}

	/** Sets who is in the top, telling the players online who just got there. Nobody is told on the first read. */
	private void crown(Set<UUID> top) {
		var previous = legendary;
		legendary = top;
		var config = EvwConfig.get().legendary();
		if (previous == null || !config.enabled()) {
			return;
		}
		for (UUID uuid : top) {
			var player = previous.contains(uuid) ? null : server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				player.sendSystemMessage(Component.literal("You are among the top " + config.topCount() + " collectors! You wear the ").withStyle(ChatFormatting.LIGHT_PURPLE)
						.append(Component.literal(config.hat().name()).withStyle(ChatFormatting.DARK_PURPLE)).append("."));
				play(player, SoundEvents.ENDER_CHEST_OPEN, 0.8f);
			}
		}
	}
}
