package nu.metacraft.rivals;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import nu.metacraft.rivals.gun.InkOnScreen;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.Roll;
import nu.metacraft.rivals.gun.SpecialChoice;
import nu.metacraft.rivals.gun.SpecialDialog;
import nu.metacraft.rivals.gun.Weapon;
import nu.metacraft.rivals.gun.WeaponChoice;
import nu.metacraft.rivals.gun.WeaponDialog;
import nu.metacraft.rivals.gun.WeaponLock;
import nu.metacraft.rivals.gun.WeaponPicks;
import nu.metacraft.rivals.gun.WeaponSelector;
import nu.metacraft.rivals.paint.PaintTally;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The round loop: one match at a time, for the whole server, in memory.
 *
 * <pre>LOBBY → COUNTDOWN (5 s) → PLAYING (n minutes) → ENDED (10 s) → LOBBY</pre>
 *
 * <p>Everything the clock does goes through {@link #tick(MinecraftServer, long)} with the tick count
 * handed in, so the whole machine can be driven by a test at whatever speed it likes rather than waited
 * out in real time. Nothing here reads {@code server.getTickCount()} on its own.
 *
 * <p><b>The roster is handed in too.</b> {@link #start} takes a supplier of the players and only
 * <em>defaults</em> to the online player list, which is the same seam {@link Readiness} has: a test can
 * hand in its own mock players without them being logged in.
 *
 * <p>A player's side is their vanilla scoreboard team and nothing else — the two team names come from
 * {@link TeamNames} — so {@link PaintColor#byTeam} is the single question asked about anybody.
 *
 * <p><b>Frozen</b> means a −100 % {@code MOVEMENT_SPEED} modifier and a −100 % {@code JUMP_STRENGTH} one,
 * transient attribute modifiers by id exactly as the roller's speed bonus is, rather than potion effects:
 * they are exact, they do not show up in the client's effect list and they come off by id. Used for the
 * countdown, for the ten seconds after the whistle and for the three a respawn costs.
 *
 * <p>Every transition wipes {@link InkOnScreen} and stops any {@link Roll} for everybody: ink on the
 * glass is health you lost in a round that is over, and a roll that survived a teleport is a player
 * rolling on a spawn platform.
 */
public final class Match {
	public enum State { LOBBY, COUNTDOWN, PLAYING, ENDED }

	/** The countdown, the celebration and the freeze a respawn costs, in ticks. */
	public static final int COUNTDOWN_TICKS = 100;
	public static final int ENDED_TICKS = 200;
	public static final int RESPAWN_FREEZE_TICKS = 60;
	/** How long a match may be asked to run, in minutes. */
	public static final int MIN_MINUTES = 1, MAX_MINUTES = 60;
	/** The fireworks the winner gets, and how long they are spread over. */
	public static final int FIREWORKS = 10;
	public static final int FIREWORK_SPREAD_TICKS = 60;

	/** The freeze, by id, so it can be taken off again — the same shape as {@link Roll#SPEED_ID}. */
	public static final Identifier FREEZE_SPEED_ID = Rivals.id("match/freeze_speed");
	public static final Identifier FREEZE_JUMP_ID = Rivals.id("match/freeze_jump");

	private static State state = State.LOBBY;
	/** The level the match is played in: its arena holds the spawns and the bounds. */
	private static @Nullable ServerLevel arena;
	/** The absolute tick the current state runs out. Meaningless in LOBBY. */
	private static long stateEnds;
	/** The absolute tick the current state began, for the countdown's and the fireworks' own counting. */
	private static long stateBegan;
	private static Runnable onStateEnded = () -> {};
	private static int minutes;
	private static @Nullable ServerBossEvent timer;
	private static @Nullable PaintColor winner;
	private static Map<PaintColor, Integer> finalCounts = Map.of();
	/** How many fireworks have gone up since the whistle. */
	private static int fireworksSent;
	/** The last whole second the countdown announced, so each number is titled once. */
	private static int lastCount = -1;

	/** Who is playing. Set at start; see the class note. */
	private static Supplier<List<ServerPlayer>> roster = List::of;

	/** Whose respawn freeze runs out when (absolute ticks). */
	private static final Map<UUID, Long> respawning = new HashMap<>();

	private Match() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, server.getTickCount()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
		// A player who drops out mid-match takes no boss bars with them: they are server-side per-player
		// state, and the ones this mod owns are ours to take off.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> dropBars(handler.getPlayer()));
		// A death during PLAYING puts the player back on their own team's spawn rather than at the world
		// one, frozen and invulnerable for three seconds with a title that says so. Fabric's AFTER_RESPAWN
		// hands over the new entity, which is the one that has to be moved: overriding the respawn position
		// itself would also have to answer for the bed, the anchor and the end portal.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (state != State.PLAYING || alive) return;
			respawn(newPlayer, newPlayer.level().getServer().getTickCount());
		});
	}

	// ---- reading the state

	public static State state() {
		return state;
	}

	public static boolean running() {
		return state == State.COUNTDOWN || state == State.PLAYING;
	}

	/** Ticks left in the current state; 0 in LOBBY, which has no end. */
	public static long ticksLeft(long now) {
		return state == State.LOBBY ? 0 : Math.max(0, stateEnds - now);
	}

	public static Optional<PaintColor> winner() {
		return Optional.ofNullable(winner);
	}

	/** The tally the winner was read off, empty until a match has ended. */
	public static Map<PaintColor, Integer> finalCounts() {
		return finalCounts;
	}

	public static Optional<ServerLevel> level() {
		return Optional.ofNullable(arena);
	}

	/** Is this player inside their three seconds of respawn grace? */
	public static boolean isRespawning(Player player, long now) {
		Long until = respawning.get(player.getUUID());
		return until != null && until > now;
	}

	/** "⏱ 2:05", the timer bar's own text, and what {@code /rivals match status} prints. */
	public static String clock(long ticksLeft) {
		long seconds = ticksLeft / 20;
		return String.format("⏱ %d:%02d", seconds / 60, seconds % 60);
	}

	// ---- starting

	/** What {@link #start} answered: either it began, or here is why not. */
	public record Result(boolean started, Component message) {
		public static Result no(String why) {
			return new Result(false, Component.literal(why).withStyle(ChatFormatting.RED));
		}
	}

	/** The command's form: the online players and the server's own clock. */
	public static Result start(MinecraftServer server, ServerLevel level, int minutes, boolean force) {
		return start(server, level, () -> server.getPlayerList().getPlayers(), minutes, force,
				server.getTickCount());
	}

	/**
	 * Begin a match: check everybody is on one of the two sides (unless {@code force}), make sure both
	 * teams exist, clear the arena's paint, hand out the weapon each player picked, teleport them to their
	 * side's spawn in survival, freeze them and start the countdown.
	 */
	public static Result start(MinecraftServer server, ServerLevel level, Supplier<List<ServerPlayer>> players,
			int minutes, boolean force, long now) {
		if (running()) return Result.no("A match is already running (" + state + "). /rivals match stop first.");
		if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
			return Result.no("A match is " + MIN_MINUTES + " to " + MAX_MINUTES + " minutes, not " + minutes);
		}
		Arena arenaData = Arena.of(level);
		if (!arenaData.spawnsReady()) {
			return Result.no("No spawn for every team in this level. Set them with /rivals spawn set <"
					+ PaintColor.idList() + ">");
		}
		Readiness.Report report = Readiness.of(players.get());
		if (!force && !report.ready()) {
			return Result.no(report.lines().isEmpty()
					? "Nobody is playing"
					: "Not on a team: " + report.teamlessNames() + " — /team join <" + TeamNames.nameList()
							+ ">. Add force to start anyway.");
		}
		roster = players;
		arena = level;
		Match.minutes = minutes;
		winner = null;
		finalCounts = Map.of();
		fireworksSent = 0;
		lastCount = -1;
		respawning.clear();
		// The same teams /rivals setup makes, made again: a match should not fail because nobody ran it.
		RivalsCommands.setupTeams(server);
		int teamed = 0;
		for (Readiness.Line line : report.lines()) {
			if (line.team().isEmpty()) continue;
			join(server, line.player(), line.team().get());
			teamed++;
		}
		clearArena(level);
		// After the teams are settled and the arena is clear: an empty board, and a zero for everybody
		// playing, so MAIN's outro sorts the whole roster rather than only whoever scored.
		Stats.startRound(server, roster.get());
		enter(State.COUNTDOWN, now, COUNTDOWN_TICKS);
		for (ServerPlayer player : roster.get()) freeze(player);
		askUnarmed(report);
		int playing = teamed;
		return new Result(true, Component.literal("Match starting: " + playing + " player"
				+ (playing == 1 ? "" : "s") + ", " + minutes + " minute" + (minutes == 1 ? "" : "s")
				+ ". Counting down…").withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Put a picker in front of everybody on a side who has never answered one, and return how many were
	 * asked. Both choices count: the weapon picker for whoever has no weapon, and the special picker for
	 * whoever has a weapon but has never said what F throws.
	 *
	 * <p>One screen each, never two — a second dialog would simply replace the first, and a player would
	 * answer a question they never saw the other half of. Whoever needs the weapon gets the weapon picker,
	 * and taking a weapon out of it opens the special picker on its own ({@link WeaponPicks#pick}), so the
	 * two arrive in order rather than on top of each other.
	 *
	 * <p>During the countdown, which is the one moment in a round when a player is frozen with nothing to
	 * do: five seconds is plenty to click a button, and somebody who does not answer keeps the shooter
	 * {@link #arm} has already put in their hand and the splat bomb F has always thrown. Asking a player
	 * who <em>has</em> picked would be taking a screen away from someone watching the numbers count down.
	 */
	public static int askUnarmed(Readiness.Report report) {
		int asked = 0;
		for (Readiness.Line line : report.lines()) {
			if (line.team().isEmpty()) continue;
			ServerPlayer player = line.player();
			MinecraftServer server = player.level().getServer();
			if (line.weapon().isEmpty()) {
				WeaponDialog.open(player);
			} else if (server != null && SpecialChoice.of(server).get(player).isEmpty()) {
				SpecialDialog.open(player);
			} else {
				continue;
			}
			asked++;
		}
		return asked;
	}

	/**
	 * Put one player into the match: their side's scoreboard team (which is what gives their paint a
	 * colour), the weapon they picked, their side's spawn, survival. Also what a player who joins
	 * mid-match gets.
	 */
	public static void join(MinecraftServer server, ServerPlayer player, PaintColor color) {
		PlayerTeam team = server.getScoreboard().getPlayerTeam(TeamNames.nameOf(color));
		if (team != null) server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
		// Every way into a match is through here, including a player who turns up half way, so this is
		// where the board learns their name — see Stats.remember.
		Stats.remember(player);
		arm(player);
		place(player, color);
		player.setGameMode(GameType.SURVIVAL);
		InkOnScreen.clear(player);
		Roll.stop(player);
	}

	/**
	 * A player who arrives while the match is on: same treatment, and frozen for the respawn grace rather
	 * than dropped into a firefight the instant their screen loads. Returns whether they were let in — a
	 * player on neither side is not.
	 */
	public static boolean addMidMatch(ServerPlayer player, long now) {
		if (state != State.PLAYING || arena == null) return false;
		Optional<PaintColor> color = PaintColor.byTeam(player.getTeam());
		if (color.isEmpty()) return false;
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		join(server, player, color.get());
		grace(player, now);
		return true;
	}

	/**
	 * The weapon this player picked, and nothing else in the way of it — but the weapon selector survives.
	 * It is the only way to a different weapon and it keeps its own corner of the inventory
	 * ({@link WeaponSelector#SLOT}): anybody whose selector has wandered has it put back there, and anybody
	 * who has lost theirs is given one.
	 *
	 * <p>This is the only place a selector is ever handed out ({@link WeaponSelector#give}), which is what
	 * makes {@link #disarm} stick: between matches nobody carries one, and nothing in the lobby puts it back.
	 */
	public static ItemStack arm(ServerPlayer player) {
		WeaponPicks.sweep(player);
		MinecraftServer server = player.level().getServer();
		Weapon weapon = server == null ? WeaponChoice.DEFAULT : WeaponChoice.of(server).orDefault(player);
		ItemStack gun = PaintWeapon.withTankColor(new ItemStack(PaintWeapon.of(weapon)), player.getTeam());
		WeaponPicks.intoItsSlot(player, gun);
		WeaponSelector.give(player);
		WeaponLock.pin(player);
		return gun;
	}

	/**
	 * The other half of {@link #arm}: the whole Rivals kit back off a player and nothing of theirs touched
	 * — every paint weapon wherever it is sitting, and the selector out of {@link WeaponSelector#SLOT} —
	 * which releases the weapon lock with it, since {@link WeaponLock} asks only whether there is a paint
	 * weapon in {@link WeaponPicks#GIVEN_SLOT}. Returns how many stacks were taken.
	 *
	 * <p>What the whistle does to everybody, and what the lobby does to anyone who turns up carrying a kit
	 * from a round that is over. A match is the only reason to hold any of it: a player who was in one is
	 * left with the inventory they walked in with.
	 */
	public static int disarm(ServerPlayer player) {
		return WeaponPicks.sweep(player) + WeaponSelector.take(player);
	}

	/** Move a player onto their team's spawn, if this level has one. */
	public static void place(ServerPlayer player, PaintColor color) {
		if (arena == null) return;
		Arena.of(arena).spawn(color).ifPresent(spawn -> player.teleportTo(arena, spawn.pos().x, spawn.pos().y,
				spawn.pos().z, Set.<Relative>of(), spawn.yaw(), spawn.pitch(), true));
	}

	// ---- stopping

	/**
	 * The whistle, early, from an operator ({@code /rivals match stop}). Returns whether there was a match to
	 * stop. The same ending as the clock's: the board is written and the arena's win function runs when the
	 * celebration is over.
	 */
	public static boolean stop(long now, MinecraftServer server) {
		if (!running()) return false;
		end(now, server, false);
		return true;
	}

	/**
	 * The whistle, early, from MAIN's own running flag dropping ({@link MainPack}). Returns whether there was
	 * a match to stop. The board is still written — an outro may well want the numbers however the round
	 * finished — but the arena's win function is <em>not</em> run: MAIN is already ending the game, and
	 * telling it so again would be MAIN answering itself.
	 */
	public static boolean stopQuietly(long now, MinecraftServer server) {
		if (!running()) return false;
		end(now, server, true);
		return true;
	}

	/** Everything forgotten: a server stop, and the tests. */
	public static void clearAll() {
		clearBars();
		for (ServerPlayer player : roster.get()) thaw(player);
		state = State.LOBBY;
		onStateEnded = () -> {};
		arena = null;
		winner = null;
		finalCounts = Map.of();
		respawning.clear();
		roster = List::of;
		Stats.clearAll();
		MainPack.forget();
	}

	// ---- the clock

	/**
	 * One server tick. Counts the countdown down, keeps the timer bar honest, blows the whistle at zero,
	 * sends the winner's fireworks and drops back to the lobby ten seconds later.
	 */
	public static void tick(MinecraftServer server, long now) {
		switch (state) {
			case LOBBY -> {}
			case COUNTDOWN -> countdown(server, now);
			case PLAYING -> playing(server, now);
			case ENDED -> ended(server, now);
		}
		thawExpiredRespawns(now);
	}

	private static void countdown(MinecraftServer server, long now) {
		long left = ticksLeft(now);
		if (left <= 0) {
			go(server, now);
			return;
		}
		// One title per whole second, counting 5..1. Announced once each, because a title resent every
		// tick never finishes fading and reads as a flicker.
		int count = (int) Math.ceil(left / 20.0);
		if (count == lastCount) return;
		lastCount = count;
		for (ServerPlayer player : roster.get()) {
			title(player, Component.literal(Integer.toString(count)).withStyle(ChatFormatting.YELLOW),
					Component.literal("Get ready").withStyle(ChatFormatting.GRAY));
			note(player, 0.7f + 0.1f * (5 - Math.min(5, count)));
		}
	}

	private static void go(MinecraftServer server, long now) {
		for (ServerPlayer player : roster.get()) {
			thaw(player);
			title(player, Component.literal("GO!").withStyle(ChatFormatting.GREEN), Component.empty());
			note(player, 1.6f);
		}
		enter(State.PLAYING, now, minutes * 60L * 20L);
		timer = new ServerBossEvent(UUID.randomUUID(), Component.literal(clock(ticksLeft(now))),
				BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		refreshTimer(now);
	}

	private static void playing(MinecraftServer server, long now) {
		if (ticksLeft(now) <= 0) {
			end(now, server, false);
			return;
		}
		// Once a second: the bar's text is m:ss, so there is nothing to redraw between seconds, and the
		// score bars beside it refresh on the same beat.
		if ((now - stateBegan) % 20 == 0) refreshTimer(now);
	}

	/** The timer bar beside the score bars: the clock as text, the fraction of the match as the bar. */
	private static void refreshTimer(long now) {
		if (timer == null) return;
		long left = ticksLeft(now);
		long whole = minutes * 60L * 20L;
		timer.setName(Component.literal(clock(left)));
		timer.setProgress(whole <= 0 ? 0f : Math.min(1f, (float) left / whole));
		List<ServerPlayer> playing = roster.get();
		for (ServerPlayer stale : List.copyOf(timer.getPlayers())) {
			if (!playing.contains(stale)) timer.removePlayer(stale);
		}
		for (ServerPlayer player : playing) timer.addPlayer(player);
	}

	/**
	 * The whistle: freeze everybody, count the paint, say who won in their own colour and start the
	 * winner's fireworks. The percentages are both printed, because "DATA wins" without a figure is a
	 * result nobody can argue with or learn from.
	 *
	 * <p>The MAIN datapack never ends this minigame on its own, so the arena says how: its win function for
	 * the winning side, or its draw function when there is none ({@link Arena#getWinFunction}, set with
	 * {@code /rivals win-function set} and {@code /rivals draw-function set}), run when the celebration is
	 * over and the lobby begins. {@code quiet} leaves that out — the flag-dropped stop, see
	 * {@link #stopQuietly}. The per-player board ({@link Stats}) is written either way, and before any of
	 * it, so MAIN's outro reads finished numbers.
	 */
	private static void end(long now, MinecraftServer server, boolean quiet) {
		clearBars();
		finalCounts = arena == null ? Map.of() : PaintTally.of(arena).count(arena);
		// The board before the titles: count() has just pruned the paint that is gone, so the held-paint
		// credit read off it now is the same picture the percentages are.
		if (arena != null) Stats.publish(server, arena);
		winner = decide(finalCounts);
		// Equal paint is not equal play: the side that killed more takes it. Still level after that and it
		// is a real draw, which the titles say and the arena's draw function answers.
		if (winner == null) winner = Stats.sideWithMostKills(roster.get());
		for (ServerPlayer player : roster.get()) {
			freeze(player);
			disarm(player);
			InkOnScreen.clear(player);
			Roll.stop(player);
			Component headline = winner == null
					? Component.literal("Draw").withStyle(ChatFormatting.GRAY)
					: Component.literal(winner.displayName + " wins!")
							.withStyle(style -> style.withColor(winner.teamColor.textColor()));
			title(player, headline, Component.literal(percentages(finalCounts)).withStyle(ChatFormatting.WHITE));
			player.sendSystemMessage(Component.literal(percentages(finalCounts)));
		}
		fireworksSent = 0;
		enter(
			State.ENDED, now, ENDED_TICKS, () -> {
				if (arena != null && !quiet) {
					Arena.of(arena).getWinFunction(winner).ifPresent(functions -> {
						functions.getFunctions(server).forEach(function -> {
							server.getFunctions().execute(function, server.getFunctions().getGameLoopSender());
						});
					});
				}
			}
		);
	}

	/** Who painted most, or empty for a tie (including a match where nobody painted anything). */
	public static @Nullable PaintColor decide(Map<PaintColor, Integer> counts) {
		PaintColor best = null;
		int most = -1;
		boolean tied = false;
		for (PaintColor color : PaintColor.values()) {
			int faces = counts.getOrDefault(color, 0);
			if (faces > most) {
				most = faces;
				best = color;
				tied = false;
			} else if (faces == most) {
				tied = true;
			}
		}
		return tied || most <= 0 ? null : best;
	}

	/** "DATA 61 % · IT 39 %", every team named whether it won or not. */
	public static String percentages(Map<PaintColor, Integer> counts) {
		List<String> parts = new ArrayList<>();
		Map<PaintColor, Integer> full = new EnumMap<>(PaintColor.class);
		for (PaintColor color : PaintColor.values()) full.put(color, counts.getOrDefault(color, 0));
		for (PaintColor color : PaintColor.values()) {
			parts.add(color.displayName + " " + Math.round(PaintTally.share(full, color) * 100) + " %");
		}
		return String.join(" · ", parts);
	}

	private static void ended(MinecraftServer server, long now) {
		fireworks(now);
		if (ticksLeft(now) <= 0) lobby(server, now);
	}

	/** Ten rockets in the winner's colour over three seconds, at their spawn. A draw gets none. */
	private static void fireworks(long now) {
		if (winner == null || arena == null || fireworksSent >= FIREWORKS) return;
		long since = now - stateBegan;
		int due = (int) Math.min(FIREWORKS, since * FIREWORKS / FIREWORK_SPREAD_TICKS + 1);
		Optional<Arena.Spawn> spawn = Arena.of(arena).spawn(winner);
		if (spawn.isEmpty()) return;
		while (fireworksSent < due) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
					FireworkExplosion.Shape.LARGE_BALL, IntList.of(winner.rgb), IntList.of(0xFFFFFF), true, false))));
			arena.addFreshEntity(new FireworkRocketEntity(arena, rocket,
					spawn.get().pos().x, spawn.get().pos().y + 1, spawn.get().pos().z, true));
			fireworksSent++;
		}
	}

	private static void onStateEnded() {
		onStateEnded.run();
		onStateEnded = () -> {};
	}

	/**
	 * Back to the lobby: nobody frozen, nobody's screen inked, nobody rolling — and then {@link Lobby},
	 * which is what decides what being between matches means for a player (adventure mode, empty hands,
	 * one selector).
	 */
	private static void lobby(MinecraftServer server, long now) {
		state = State.LOBBY;
		stateEnds = now;
		stateBegan = now;
		onStateEnded();
		clearBars();
		List<ServerPlayer> players = roster.get();
		for (ServerPlayer player : players) {
			thaw(player);
			InkOnScreen.clear(player);
			Roll.stop(player);
		}
		Lobby.receiveAll(players);
	}

	private static void enter(State next, long now, long ticks) {
		enter(next, now, ticks, () -> {});
	}

	private static void enter(State next, long now, long ticks, Runnable action) {
		onStateEnded();
		state = next;
		stateBegan = now;
		stateEnds = now + ticks;
		onStateEnded = action;
	}

	// ---- death and respawn

	/** A player who just died in a live match: their own spawn, three frozen seconds, a clean screen. */
	static void respawn(ServerPlayer player, long now) {
		PaintColor.byTeam(player.getTeam()).ifPresent(color -> place(player, color));
		InkOnScreen.clear(player);
		Roll.stop(player);
		arm(player);
		grace(player, now);
		title(player, Component.literal("Respawning").withStyle(ChatFormatting.AQUA), Component.empty());
	}

	/** Three seconds frozen and untouchable, so a respawn is not a free kill for whoever is standing there. */
	private static void grace(ServerPlayer player, long now) {
		respawning.put(player.getUUID(), now + RESPAWN_FREEZE_TICKS);
		freeze(player);
		player.setPermanentlyInvulnerable(true);
	}

	private static void thawExpiredRespawns(long now) {
		if (respawning.isEmpty()) return;
		List<ServerPlayer> players = roster.get();
		respawning.entrySet().removeIf(entry -> {
			if (entry.getValue() > now) return false;
			for (ServerPlayer player : players) {
				if (!player.getUUID().equals(entry.getKey())) continue;
				player.setPermanentlyInvulnerable(false);
				// Only the countdown and the end freeze everybody; in PLAYING the grace is the only freeze
				// there is, so this is the one that has to come off.
				if (state == State.PLAYING) thaw(player);
			}
			return true;
		});
	}

	// ---- the boss bars

	/**
	 * Every Rivals boss bar off every screen: the timer, and the score bars beside it
	 * ({@link ScoreBars#clear}). The whistle's job as much as the freeze is — a match that is over must not
	 * leave "DATA 100 %" hanging over the lobby — and the server stop's.
	 */
	private static void clearBars() {
		if (timer != null) {
			timer.removeAllPlayers();
			timer = null;
		}
		ScoreBars.clear();
	}

	/**
	 * Every Rivals boss bar off one screen: what a disconnect mid-match gets, because a bar is per-player
	 * state on the server and a player who logs back in should not be told about a bar nobody removed them
	 * from.
	 */
	public static void dropBars(ServerPlayer player) {
		if (timer != null) timer.removePlayer(player);
		ScoreBars.drop(player);
	}

	/** Is any Rivals boss bar on this player's screen? What the tests read. */
	public static boolean showsAnyBar(ServerPlayer player) {
		return (timer != null && timer.getPlayers().contains(player)) || ScoreBars.shows(player);
	}

	// ---- the freeze, and the little things

	/** Is this player frozen? What the tests read. */
	public static boolean isFrozen(Player player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(FREEZE_SPEED_ID);
	}

	public static void freeze(Player player) {
		modifier(player, Attributes.MOVEMENT_SPEED, FREEZE_SPEED_ID, -1.0);
		modifier(player, Attributes.JUMP_STRENGTH, FREEZE_JUMP_ID, -1.0);
	}

	public static void thaw(Player player) {
		remove(player, Attributes.MOVEMENT_SPEED, FREEZE_SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, FREEZE_JUMP_ID);
	}

	/** The same shape {@link Roll} uses: a transient modifier by id, exact and invisible to the client. */
	private static void modifier(Player player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) return;
		AttributeModifier existing = instance.getModifier(id);
		if (existing != null && existing.amount() == amount) return;
		instance.addOrUpdateTransientModifier(
				new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void remove(Player player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.removeModifier(id);
	}

	/** Clear the paint the match is played on: inside the arena's bounds when it has any. */
	static int clearArena(ServerLevel level) {
		return Arena.of(level).box()
				.map(box -> PaintTally.of(level).reset(level, box))
				.orElseGet(() -> PaintTally.of(level).reset(level));
	}

	private static void title(ServerPlayer player, Component headline, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(headline));
	}

	/** A note block under every title: the countdown is a rhythm, and a rhythm needs a sound. */
	private static void note(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, pitch);
	}
}
