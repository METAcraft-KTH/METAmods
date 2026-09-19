package nu.metacraft.rivals;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import nu.metacraft.lib.util.FunctionOrTag;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.Special;
import nu.metacraft.rivals.gun.SpecialDialog;
import nu.metacraft.rivals.gun.SpecialTuning;
import nu.metacraft.rivals.gun.Weapon;
import nu.metacraft.rivals.gun.WeaponDialog;
import nu.metacraft.rivals.gun.WeaponPicks;
import nu.metacraft.rivals.gun.WeaponTuning;
import nu.metacraft.rivals.gun.WeaponTuning.Param;
import nu.metacraft.rivals.paint.PaintTally;
import nu.metacraft.rivals.paint.Unpaintable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /rivals setup | gun [weapon] | kit | score | reset | reload | ready | match | tune} for game masters
 * (permission {@code metacraft.rivals}), and {@code /rivals weapons} and {@code /rivals special} for
 * everybody.
 *
 * <p>The arena half — {@code spawn set|list}, {@code arena set|clear|show} — is the same permission: it
 * edits {@link Arena}, the level's saved spawns and bounds.
 *
 * <p>The permission is per subcommand rather than on the {@code rivals} root, because one of them is
 * not an admin act: picking your own weapon out of {@link WeaponDialog} is something every player in the
 * lobby does, and a root-level {@code requires} would have hidden the whole tree from them.
 */
public final class RivalsCommands {
	private RivalsCommands() {}

	/** Who may run the admin half of the tree. */
	private static final Predicate<CommandSourceStack> ADMIN =
			source -> Permissions.check(source, "metacraft.rivals", PermissionLevel.GAMEMASTERS);

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				literal("rivals")
						.then(literal("setup").requires(ADMIN).executes(ctx -> {
							List<String> made = setupTeams(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Teams ready — " + TeamNames.describe()
									+ ". " + (made.isEmpty() ? "All of them existed already" : "Made: " + String.join(", ", made))
									+ ". Join with /team join <" + TeamNames.nameList() + "> @s"), true);
							return made.size();
						}))
						// A plain word rather than a registry or enum argument: the ids are the weapon's own, and an
						// unknown one should say what is on offer instead of failing to parse.
						.then(literal("gun").requires(ADMIN)
								.executes(ctx -> gun(ctx.getSource(), Weapon.SHOOTER))
								.then(argument("weapon", StringArgumentType.word()).executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "weapon");
									Optional<Weapon> weapon = Weapon.byId(id);
									if (weapon.isEmpty()) {
										ctx.getSource().sendFailure(Component.literal("No weapon called \"" + id + "\". Try one of: " + Weapon.idList())
												.withStyle(ChatFormatting.RED));
										return 0;
									}
									return gun(ctx.getSource(), weapon.get());
								})))
						// Everything a shot is made of, live. Weapons and parameters are plain words rather than
						// enum arguments for the same reason /rivals gun is: an unknown one should answer with
						// what is on offer instead of failing to parse, and "reset" sits in the same slot.
						.then(literal("tune").requires(ADMIN)
								.executes(ctx -> tuneAll(ctx.getSource()))
								// A literal in the weapon's place, because a special is not a weapon and no
								// weapon answers to the word: /rivals tune special <id> <param> <value>, with
								// the same shape as a weapon's sheet and the same "reset" in either slot.
								.then(literal("special")
										.executes(ctx -> tuneSpecials(ctx.getSource()))
										.then(argument("special", StringArgumentType.word()).suggests(SPECIALS)
												.executes(ctx -> tuneSpecial(ctx.getSource(), StringArgumentType.getString(ctx, "special")))
												.then(argument("param", StringArgumentType.word()).suggests(SPECIAL_PARAMS)
														.executes(ctx -> tuneSpecialParam(ctx.getSource(), StringArgumentType.getString(ctx, "special"),
																StringArgumentType.getString(ctx, "param")))
														.then(argument("value", DoubleArgumentType.doubleArg())
																.executes(ctx -> tuneSpecialSet(ctx.getSource(), StringArgumentType.getString(ctx, "special"),
																		StringArgumentType.getString(ctx, "param"),
																		DoubleArgumentType.getDouble(ctx, "value")))))))
								.then(argument("weapon", StringArgumentType.word()).suggests(WEAPONS)
										.executes(ctx -> tuneWeapon(ctx.getSource(), StringArgumentType.getString(ctx, "weapon")))
										.then(argument("param", StringArgumentType.word()).suggests(PARAMS)
												.executes(ctx -> tuneParam(ctx.getSource(), StringArgumentType.getString(ctx, "weapon"),
														StringArgumentType.getString(ctx, "param")))
												.then(argument("value", DoubleArgumentType.doubleArg())
														.executes(ctx -> tuneSet(ctx.getSource(), StringArgumentType.getString(ctx, "weapon"),
																StringArgumentType.getString(ctx, "param"),
																DoubleArgumentType.getDouble(ctx, "value")))))))
						.then(literal("kit").requires(ADMIN).executes(ctx -> kit(ctx.getSource())))
						.then(literal("score").requires(ADMIN).executes(ctx -> score(ctx.getSource())))
						.then(literal("reset").requires(ADMIN).executes(ctx -> reset(ctx.getSource())))
						.then(literal("reload").requires(ADMIN).executes(ctx -> reload(ctx.getSource())))
						// Where each team starts: the sender's own stance, because a look direction is not
						// something anybody wants to type as two numbers.
						.then(literal("spawn").requires(ADMIN)
								.then(literal("list").executes(ctx -> spawnList(ctx.getSource())))
								.then(literal("set").then(argument("team", StringArgumentType.word()).suggests(TEAMS)
										.executes(ctx -> spawnSet(ctx.getSource(), StringArgumentType.getString(ctx, "team"))))))
						.then(literal("win-function").requires(ADMIN)
							.then(literal("show").then(argument("team", StringArgumentType.word()).suggests(TEAMS)
									.executes(ctx -> showFunction(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
							.then(literal("set").then(argument("team", StringArgumentType.word()).suggests(TEAMS)
								.then(argument("functions", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION)
									.executes(ctx -> setFunction(ctx.getSource(), StringArgumentType.getString(ctx, "team"), FunctionOrTag.fromArgument(ctx, "functions")))))))
						.then(literal("draw-function").requires(ADMIN)
							.then(literal("show").executes(ctx -> showFunction(ctx.getSource())))
							.then(literal("set")
								.then(argument("functions", FunctionArgument.functions()).suggests(FunctionCommand.SUGGEST_FUNCTION)
									.executes(ctx -> setFunction(ctx.getSource(), FunctionOrTag.fromArgument(ctx, "functions"))))))
						// Where the arena ends. While a box is set, paint outside it is refused and a reset
						// clears only what is inside it.
						.then(literal("arena").requires(ADMIN)
								.then(literal("set")
										.then(argument("from", BlockPosArgument.blockPos())
												.then(argument("to", BlockPosArgument.blockPos())
														.executes(ctx -> arenaSet(ctx.getSource(),
																BlockPosArgument.getLoadedBlockPos(ctx, "from"),
																BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
								.then(literal("clear").executes(ctx -> arenaClear(ctx.getSource())))
								.then(literal("show").executes(ctx -> arenaShow(ctx.getSource()))))
						// The round loop.
						.then(literal("match").requires(ADMIN)
								.then(literal("start")
										.then(argument("minutes", IntegerArgumentType.integer(Match.MIN_MINUTES, Match.MAX_MINUTES))
												.executes(ctx -> matchStart(ctx.getSource(),
														IntegerArgumentType.getInteger(ctx, "minutes"), false))
												// A plain word rather than a boolean: "force" is what an operator
												// types, and "true" says nothing about what is being forced.
												.then(literal("force").executes(ctx -> matchStart(ctx.getSource(),
														IntegerArgumentType.getInteger(ctx, "minutes"), true)))))
								.then(literal("stop").executes(ctx -> matchStop(ctx.getSource())))
								.then(literal("status").executes(ctx -> matchStatus(ctx.getSource()))))
						// Who is here, dressed and armed — and a failure if anybody is not.
						.then(literal("ready").requires(ADMIN).executes(ctx -> Readiness.report(ctx.getSource())))
						// No permission: every player picks their own weapon. The dialog's buttons run the
						// pick as the player who clicked them, so it has to be theirs to run.
						.then(literal("weapons").executes(ctx -> weapons(ctx.getSource()))
								.then(literal("pick").then(argument("weapon", StringArgumentType.word()).suggests(WEAPON_IDS)
										.executes(ctx -> weaponPick(ctx.getSource(),
												StringArgumentType.getString(ctx, "weapon"))))))
						// The other half of a loadout, and no permission on this one either: what F throws is
						// the thrower's own business, and the picker's buttons are theirs to run.
						.then(literal("special").executes(ctx -> special(ctx.getSource()))
								.then(literal("pick").then(argument("special", StringArgumentType.word()).suggests(SPECIAL_IDS)
										.executes(ctx -> specialPick(ctx.getSource(),
												StringArgumentType.getString(ctx, "special"))))))));
	}

	/**
	 * One vanilla team per side, under the name {@link TeamNames} says that side uses. Returns the names
	 * of the teams it had to create, which is what the message reports.
	 *
	 * <p>A team that already exists keeps its own name and colour: it may well be the server's own team,
	 * pointed at a side by the config, and renaming or recolouring somebody else's team because a match is
	 * being set up would be rude. What is applied either way is the two rules a match needs to work — no
	 * friendly fire and no collision — because those are mechanics rather than presentation.
	 */
	public static List<String> setupTeams(MinecraftServer server) {
		ServerScoreboard board = server.getScoreboard();
		List<String> made = new ArrayList<>();
		for (PaintColor color : PaintColor.values()) {
			String name = TeamNames.nameOf(color);
			PlayerTeam team = board.getPlayerTeam(name);
			if (team == null) {
				team = board.addPlayerTeam(name);
				team.setDisplayName(Component.literal(color.displayName));
				team.setColor(Optional.of(color.teamColor));
				made.add(name);
			}
			team.setAllowFriendlyFire(false);
			team.setCollisionRule(Team.CollisionRule.NEVER);
		}
		return made;
	}

	private static int gun(CommandSourceStack source, Weapon weapon) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gun = new ItemStack(PaintWeapon.of(weapon));
		if (!player.getInventory().add(gun)) player.drop(gun, false, Prediction.SERVER_ONLY);
		source.sendSuccess(() -> Component.literal("Here is a " + weapon.displayName + ". Right-click to fire; join a team for colour."), false);
		return 1;
	}

	private static int kit(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int given = PaintWeapon.giveKit(player);
		source.sendSuccess(() -> Component.literal("Here is the full kit: " + Weapon.idList()), false);
		return given;
	}

	/**
	 * What is painted in this level. With arena bounds set the arena is swept for paint blocks rather than
	 * read off the tracking, so paint that was standing before the last restart is counted too.
	 */
	private static int score(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<BoundingBox> bounds = Arena.of(level).box();
		Map<PaintColor, Integer> counts = bounds.map(box -> PaintTally.of(level).count(level, box))
				.orElseGet(() -> PaintTally.of(level).count(level));
		int total = 0;
		for (int n : counts.values()) total += n;
		// Per level, unlike the bossbars, which sum every level; name it so the two cannot be confused.
		source.sendSuccess(() -> Component.literal("Paint in " + level.dimension().identifier() + ":"), false);
		if (total == 0) {
			source.sendSuccess(() -> Component.literal(bounds.isPresent()
					? "Nothing painted"
					: "Nothing painted (no arena bounds: only paint placed since the server started is known)"), false);
			return 0;
		}
		for (PaintColor color : PaintColor.values()) {
			int faces = counts.get(color);
			int percent = Math.round(PaintTally.share(counts, color) * 100);
			source.sendSuccess(() -> Component.literal(color.displayName + ": " + faces + " faces, " + percent + " %")
					.withStyle(style -> style.withColor(color.teamColor.textColor())), false);
		}
		return total;
	}

	/**
	 * Clear the paint. With arena bounds set this clears only what is inside them — between rounds the
	 * arena is what wants wiping, and paint a player put on their own house outside it is not the reset's
	 * business — and it sweeps the arena itself, so paint left standing by a restart goes with the rest.
	 * With no bounds it is every cell the painter has touched in this level, as before, which after a
	 * restart is nothing: the reply says so rather than leaving a game master to wonder.
	 */
	private static int reset(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<BoundingBox> box = Arena.of(level).box();
		int removed = box.map(bounds -> PaintTally.of(level).reset(level, bounds))
				.orElseGet(() -> PaintTally.of(level).reset(level));
		String where = box.isPresent() ? " inside the arena" : "";
		if (removed == 0) {
			source.sendSuccess(() -> Component.literal("Nothing painted" + where + (box.isPresent() ? ""
					: " (no arena bounds: only paint placed since the server started is known)")), false);
		} else {
			source.sendSuccess(() -> Component.literal("Removed " + removed + " paint blocks" + where)
					.withStyle(ChatFormatting.YELLOW), true);
		}
		return removed;
	}

	/**
	 * Start a match in the sender's level. The readiness check is the same one {@code /rivals ready} runs,
	 * and {@code force} is the word that skips it.
	 */
	private static int matchStart(CommandSourceStack source, int minutes, boolean force) {
		Match.Result result = Match.start(source.getServer(), source.getLevel(), minutes, force);
		if (!result.started()) {
			source.sendFailure(result.message());
			return 0;
		}
		source.sendSuccess(result::message, true);
		return minutes;
	}

	private static int matchStop(CommandSourceStack source) {
		if (!Match.stop(source.getServer().getTickCount(), source.getServer())) {
			source.sendFailure(Component.literal("No match is running").withStyle(ChatFormatting.RED));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Match stopped").withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int matchStatus(CommandSourceStack source) {
		long now = source.getServer().getTickCount();
		Match.State state = Match.state();
		String left = state == Match.State.LOBBY ? "" : ", " + Match.clock(Match.ticksLeft(now)) + " left";
		source.sendSuccess(() -> Component.literal("Match: " + state + left), false);
		if (!Match.finalCounts().isEmpty()) {
			source.sendSuccess(() -> Component.literal("Last result: "
					+ Match.winner().map(color -> color.displayName + " won").orElse("a draw")
					+ " — " + Match.percentages(Match.finalCounts())), false);
		}
		return state == Match.State.LOBBY ? 0 : 1;
	}

	/** The team ids, for {@code /rivals spawn set}. */
	private static final SuggestionProvider<CommandSourceStack> TEAMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(PaintColor.values()).map(color -> color.id), builder);

	private static final DynamicCommandExceptionType INVALID_TEAM = new DynamicCommandExceptionType(
		teamId -> Component.literal("No team called \"" + teamId + "\". Try one of: " + PaintColor.idList())
			.withStyle(ChatFormatting.RED)
	);

	private static PaintColor getColour(String teamId) throws CommandSyntaxException {
		return PaintColor.byId(teamId).orElseThrow(() -> INVALID_TEAM.create(teamId));
	}

	private static int setFunction(CommandSourceStack source, FunctionOrTag function) throws CommandSyntaxException {
		Arena arena = Arena.of(source.getLevel());
		arena.setDrawFunction(function);
		source.sendSuccess(() -> Component.literal("Function set"), true);
		return 1;
	}

	private static int showFunction(CommandSourceStack source) throws CommandSyntaxException {
		Arena arena = Arena.of(source.getLevel());
		return arena.getDrawFunction().map(function -> {
			source.sendSuccess(() -> Component.literal("Draw function: " + function), true);
			return 1;
		}).orElseGet(() -> {
			source.sendFailure(Component.literal("No draw function :("));
			return 0;
		});
	}

	private static int setFunction(CommandSourceStack source, String teamId, FunctionOrTag function) throws CommandSyntaxException {
		var color = getColour(teamId);
		Arena arena = Arena.of(source.getLevel());
		arena.setWinFunction(color, function);
		source.sendSuccess(() -> Component.literal(color.displayName + " win function set succesfully")
			.withStyle(style -> style.withColor(color.teamColor.textColor())), true);
		return 1;
	}

	private static int showFunction(CommandSourceStack source, String teamId) throws CommandSyntaxException {
		var color = getColour(teamId);
		Arena arena = Arena.of(source.getLevel());
		return arena.getWinFunction(color).map(function -> {
			source.sendSuccess(() -> Component.literal(color.displayName + " has win function: " + function)
				.withStyle(style -> style.withColor(color.teamColor.textColor())), true);
			return 1;
		}).orElseGet(() -> {
			source.sendFailure(Component.literal(color.displayName + " has no win function :(")
				.withStyle(style -> style.withColor(color.teamColor.textColor())));
			return 0;
		});
	}

	/** Take the sender's position and look as a team's spawn. */
	private static int spawnSet(CommandSourceStack source, String teamId) throws CommandSyntaxException {
		var color = getColour(teamId);
		ServerPlayer player = source.getPlayerOrException();
		Arena arena = Arena.of(source.getLevel());
		arena.setSpawn(color, player);
		Arena.Spawn spawn = arena.spawn(color).orElseThrow();
		source.sendSuccess(() -> Component.literal(color.displayName + " starts here: " + spawn)
				.withStyle(style -> style.withColor(color.teamColor.textColor())), true);
		return 1;
	}

	/** Both spawns and the box, or what is still missing. */
	private static int spawnList(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Arena arena = Arena.of(level);
		source.sendSuccess(() -> Component.literal("Arena in " + level.dimension().identifier() + ":"), false);
		int set = 0;
		for (PaintColor color : PaintColor.values()) {
			Optional<Arena.Spawn> spawn = arena.spawn(color);
			if (spawn.isPresent()) set++;
			String line = "  " + color.displayName + ": " + spawn.map(Arena.Spawn::toString).orElse("not set");
			source.sendSuccess(() -> Component.literal(line)
					.withStyle(style -> style.withColor(color.teamColor.textColor())), false);
		}
		source.sendSuccess(() -> Component.literal("  bounds: " + arena.box()
				.map(box -> box.minX() + " " + box.minY() + " " + box.minZ() + " to "
						+ box.maxX() + " " + box.maxY() + " " + box.maxZ())
				.orElse("none (the whole level)")), false);
		return set;
	}

	private static int arenaSet(CommandSourceStack source, BlockPos from, BlockPos to) {
		Arena arena = Arena.of(source.getLevel());
		arena.setBox(from, to);
		BoundingBox box = arena.box().orElseThrow();
		long blocks = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
		source.sendSuccess(() -> Component.literal("Arena bounds set: " + box.minX() + " " + box.minY() + " " + box.minZ()
				+ " to " + box.maxX() + " " + box.maxY() + " " + box.maxZ() + " (" + blocks + " blocks). "
				+ "Paint outside them is refused.").withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int arenaClear(CommandSourceStack source) {
		boolean had = Arena.of(source.getLevel()).clearBox();
		source.sendSuccess(() -> Component.literal(had
				? "Arena bounds cleared: the whole level takes paint again"
				: "There were no arena bounds").withStyle(ChatFormatting.YELLOW), true);
		return had ? 1 : 0;
	}

	/** The box drawn in end rods for ten seconds, and its corners printed for whoever cannot see them. */
	private static int arenaShow(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		Optional<BoundingBox> box = Arena.of(level).box();
		if (box.isEmpty()) {
			source.sendFailure(Component.literal("No arena bounds in this level. Set them with /rivals arena set <from> <to>")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		Arena.show(level, player);
		BoundingBox bounds = box.get();
		source.sendSuccess(() -> Component.literal("Arena outline for " + Arena.SHOW_TICKS / 20 + " s: "
				+ bounds.minX() + " " + bounds.minY() + " " + bounds.minZ() + " to "
				+ bounds.maxX() + " " + bounds.maxY() + " " + bounds.maxZ()), false);
		return 1;
	}

	/** Open the weapon picker on the sender's own screen. */
	private static int weapons(CommandSourceStack source) throws CommandSyntaxException {
		WeaponDialog.open(source.getPlayerOrException());
		return 1;
	}

	/** The same for the special picker, which the weapon picker's last button also runs. */
	private static int special(CommandSourceStack source) throws CommandSyntaxException {
		SpecialDialog.open(source.getPlayerOrException());
		return 1;
	}

	/** The special ids, for {@code /rivals special pick} and for {@code /rivals tune special}. */
	private static final SuggestionProvider<CommandSourceStack> SPECIAL_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(Special.values()).map(Special::commandId), builder);

	/**
	 * Take a special: what the picker's buttons run, as the player who clicked one. Any player, the same
	 * as a weapon pick — a dialog button is a command the client sends, and this one has to be theirs.
	 */
	private static int specialPick(CommandSourceStack source, String id) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Optional<Special> special = Special.byId(id);
		if (special.isEmpty()) {
			source.sendFailure(Component.literal("No special called \"" + id + "\". Try one of: " + Special.idList())
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		SpecialDialog.pick(player, special.get());
		return 1;
	}

	/** Weapon ids only — the picker's own buttons, without {@code /rivals tune}'s {@code reset}. */
	private static final SuggestionProvider<CommandSourceStack> WEAPON_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(Weapon.values()).map(Weapon::commandId), builder);

	/**
	 * Take a weapon: what the picker's buttons run, as the player who clicked one. Also typeable, which is
	 * how it is tested — a dialog button is a command the client sends, and there is nothing else to it.
	 */
	private static int weaponPick(CommandSourceStack source, String id) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Optional<Weapon> weapon = Weapon.byId(id);
		if (weapon.isEmpty()) {
			source.sendFailure(Component.literal("No weapon called \"" + id + "\". Try one of: " + Weapon.idList())
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		WeaponPicks.pick(player, weapon.get());
		return 1;
	}

	/**
	 * Re-read the config files an arena builder edits between rounds: the unpaintable list, the team names
	 * and how long a round MAIN starts runs. Not the weapon tuning — that is edited from inside the game
	 * and written after every change,
	 * so re-reading it would throw away what {@code /rivals tune} just set.
	 */
	public static int reload(CommandSourceStack source) {
		TeamNames.reload();
		source.sendSuccess(() -> Component.literal("Teams — " + TeamNames.describe() + ", from "
				+ TeamNames.configPath() + ". Run /rivals setup to make any that do not exist yet."), true);
		int listed = Unpaintable.reload();
		source.sendSuccess(() -> Component.literal("Unpaintable: the #" + Rivals.MOD_ID + ":unpaintable tag plus "
				+ listed + " block" + (listed == 1 ? "" : "s") + " from " + Unpaintable.configPath()), true);
		int minutes = MainPack.reload();
		source.sendSuccess(() -> Component.literal("MAIN: a round started by " + MainPack.RUNNING_HOLDER + " in "
				+ MainPack.STATE_OBJECTIVE + " runs " + minutes + " minute" + (minutes == 1 ? "" : "s") + ", from "
				+ MainPack.configPath()), true);
		return listed;
	}

	/** Weapon ids, plus the {@code reset} that takes the whole lot back to the defaults. */
	private static final SuggestionProvider<CommandSourceStack> WEAPONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Stream.concat(Stream.of(Weapon.values()).map(Weapon::commandId), Stream.of("reset")), builder);

	/**
	 * The parameters the weapon already typed answers to, plus its own {@code reset}. A weapon nobody
	 * recognises suggests nothing rather than everything: the next word is only meaningful once the
	 * first one is, and the failure message is where the valid names belong.
	 */
	private static final SuggestionProvider<CommandSourceStack> PARAMS = (ctx, builder) -> {
		Optional<Weapon> weapon = Weapon.byId(StringArgumentType.getString(ctx, "weapon"));
		if (weapon.isEmpty()) return builder.buildFuture();
		return SharedSuggestionProvider.suggest(
				Stream.concat(WeaponTuning.params(weapon.get()).stream().map(param -> param.id), Stream.of("reset")), builder);
	};

	/**
	 * Every weapon's tuning that is off its default, and every special's, or a word to say that none of
	 * it is. Both sheets, because {@code /rivals tune} with nothing after it is the question "what has
	 * been changed here", and a specials sheet that only appeared when asked for by name would be a
	 * tuning session's worth of changes nobody was told about.
	 */
	private static int tuneAll(CommandSourceStack source) {
		if (WeaponTuning.allDefault() && SpecialTuning.allDefault()) {
			source.sendSuccess(() -> Component.literal("Weapon and special tuning: all defaults. "
					+ WeaponTuning.configPath() + ", " + SpecialTuning.configPath()), false);
			return 0;
		}
		int changed = 0;
		for (Weapon weapon : Weapon.values()) {
			WeaponTuning tuning = WeaponTuning.get(weapon);
			List<Param> params = tuning.changed();
			if (params.isEmpty()) continue;
			changed += params.size();
			String line = weapon.commandId() + ": " + params.stream()
					.map(param -> param.id + " " + WeaponTuning.number(tuning.value(param)) + " [" + WeaponTuning.number(tuning.defaultValue(param)) + "]")
					.collect(Collectors.joining(", "));
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return changed + specialLines(source);
	}

	/** The special ids, plus the {@code reset} that takes all three back to their defaults. */
	private static final SuggestionProvider<CommandSourceStack> SPECIALS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Stream.concat(Stream.of(Special.values()).map(Special::commandId), Stream.of("reset")), builder);

	/** The parameters the special already typed answers to, plus its own {@code reset}. */
	private static final SuggestionProvider<CommandSourceStack> SPECIAL_PARAMS = (ctx, builder) -> {
		Optional<Special> special = Special.byId(StringArgumentType.getString(ctx, "special"));
		if (special.isEmpty()) return builder.buildFuture();
		return SharedSuggestionProvider.suggest(Stream.concat(
				SpecialTuning.params(special.get()).stream().map(param -> param.id), Stream.of("reset")), builder);
	};

	/** Every special's tuning that is off its default, or a word to say that none of it is. */
	private static int tuneSpecials(CommandSourceStack source) {
		if (SpecialTuning.allDefault()) {
			source.sendSuccess(() -> Component.literal("Special tuning: all defaults. " + SpecialTuning.configPath()), false);
			return 0;
		}
		return specialLines(source);
	}

	/** The changed lines of every special's sheet, and only those. Shared with {@link #tuneAll}. */
	private static int specialLines(CommandSourceStack source) {
		int changed = 0;
		for (Special special : Special.values()) {
			SpecialTuning tuning = SpecialTuning.get(special);
			List<SpecialTuning.Param> params = tuning.changed();
			if (params.isEmpty()) continue;
			changed += params.size();
			String line = special.commandId() + ": " + params.stream()
					.map(param -> param.id + " " + WeaponTuning.number(tuning.value(param))
							+ " [" + WeaponTuning.number(tuning.defaultValue(param)) + "]")
					.collect(Collectors.joining(", "));
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return changed;
	}

	/**
	 * One special's whole sheet, defaults in brackets behind anything that has moved — or, for the word
	 * {@code reset} in the special's place, all three back to the numbers they shipped with.
	 */
	private static int tuneSpecial(CommandSourceStack source, String specialId) {
		if ("reset".equalsIgnoreCase(specialId)) {
			int changed = 0;
			for (Special special : Special.values()) changed += SpecialTuning.get(special).changed().size();
			SpecialTuning.resetAll();
			SpecialTuning.save();
			int total = changed;
			source.sendSuccess(() -> Component.literal("Every special back to its defaults: " + total + " values")
					.withStyle(ChatFormatting.YELLOW), true);
			return total;
		}
		Optional<Special> found = specialOr(source, specialId);
		if (found.isEmpty()) return 0;
		Special special = found.get();
		SpecialTuning tuning = SpecialTuning.get(special);
		List<SpecialTuning.Param> params = SpecialTuning.params(special);
		source.sendSuccess(() -> Component.literal(special.displayName + " (" + special.commandId() + ")")
				.withStyle(ChatFormatting.AQUA), false);
		for (SpecialTuning.Param param : params) {
			boolean untouched = tuning.isDefault(param);
			String line = "  " + param.id + ": " + WeaponTuning.number(tuning.value(param))
					+ (untouched ? "" : " [" + WeaponTuning.number(tuning.defaultValue(param)) + "]")
					+ "  (" + param.range() + ")";
			source.sendSuccess(() -> Component.literal(line).withStyle(untouched ? ChatFormatting.GRAY : ChatFormatting.WHITE), false);
		}
		return params.size();
	}

	/** One number — or, for {@code reset} in the parameter's place, this special's whole sheet. */
	private static int tuneSpecialParam(CommandSourceStack source, String specialId, String paramId) {
		Optional<Special> found = specialOr(source, specialId);
		if (found.isEmpty()) return 0;
		Special special = found.get();
		SpecialTuning tuning = SpecialTuning.get(special);
		if ("reset".equalsIgnoreCase(paramId)) {
			int changed = tuning.changed().size();
			tuning.reset();
			SpecialTuning.save();
			source.sendSuccess(() -> Component.literal(special.displayName + " back to its defaults: " + changed + " values")
					.withStyle(ChatFormatting.YELLOW), true);
			return changed;
		}
		Optional<SpecialTuning.Param> wanted = specialParamOr(source, special, paramId);
		if (wanted.isEmpty()) return 0;
		SpecialTuning.Param param = wanted.get();
		String line = special.commandId() + " " + param.id + ": " + WeaponTuning.number(tuning.value(param))
				+ (tuning.isDefault(param) ? " (default)" : " [default " + WeaponTuning.number(tuning.defaultValue(param)) + "]")
				+ ", " + param.range();
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Move one of a special's numbers and write the file. Old → new, the same as a weapon's. */
	private static int tuneSpecialSet(CommandSourceStack source, String specialId, String paramId, double value) {
		Optional<Special> found = specialOr(source, specialId);
		if (found.isEmpty()) return 0;
		Special special = found.get();
		Optional<SpecialTuning.Param> wanted = specialParamOr(source, special, paramId);
		if (wanted.isEmpty()) return 0;
		SpecialTuning.Param param = wanted.get();
		if (!param.holds(value)) {
			source.sendFailure(Component.literal(param.id + " must be " + param.range() + ", not "
					+ WeaponTuning.number(value)).withStyle(ChatFormatting.RED));
			return 0;
		}
		SpecialTuning tuning = SpecialTuning.get(special);
		double was = tuning.set(param, value);
		SpecialTuning.save();
		source.sendSuccess(() -> Component.literal(special.commandId() + " " + param.id + ": "
				+ WeaponTuning.number(was) + " \u2192 " + WeaponTuning.number(value)
				+ (tuning.isDefault(param) ? " (the default)" : " [default " + WeaponTuning.number(tuning.defaultValue(param)) + "]")), true);
		return 1;
	}

	/** The named special, or a failure that says which names there are. */
	private static Optional<Special> specialOr(CommandSourceStack source, String id) {
		Optional<Special> special = Special.byId(id);
		if (special.isEmpty()) {
			source.sendFailure(Component.literal("No special called \"" + id + "\". Try one of: " + Special.idList())
					.withStyle(ChatFormatting.RED));
		}
		return special;
	}

	/** The named parameter of that special, or a failure that lists the ones it has. */
	private static Optional<SpecialTuning.Param> specialParamOr(CommandSourceStack source, Special special, String id) {
		Optional<SpecialTuning.Param> param = SpecialTuning.Param.byId(id)
				.filter(found -> SpecialTuning.applies(special, found));
		if (param.isEmpty()) {
			source.sendFailure(Component.literal("The " + special.displayName + " has no parameter called \"" + id
					+ "\". Try one of: " + SpecialTuning.paramList(special)).withStyle(ChatFormatting.RED));
		}
		return param;
	}

	/**
	 * One weapon's whole sheet, defaults in brackets behind anything that has moved — or, for the word
	 * {@code reset} in the weapon's place, every weapon back to the numbers it shipped with.
	 */
	private static int tuneWeapon(CommandSourceStack source, String weaponId) {
		if ("reset".equalsIgnoreCase(weaponId)) {
			int changed = 0;
			for (Weapon weapon : Weapon.values()) changed += WeaponTuning.get(weapon).changed().size();
			WeaponTuning.resetAll();
			WeaponTuning.save();
			int total = changed;
			source.sendSuccess(() -> Component.literal("Every weapon back to its defaults: " + total + " values")
					.withStyle(ChatFormatting.YELLOW), true);
			return total;
		}
		Optional<Weapon> found = weaponOr(source, weaponId);
		if (found.isEmpty()) return 0;
		Weapon weapon = found.get();
		WeaponTuning tuning = WeaponTuning.get(weapon);
		List<Param> params = WeaponTuning.params(weapon);
		source.sendSuccess(() -> Component.literal(weapon.displayName + " (" + weapon.commandId() + ")")
				.withStyle(ChatFormatting.AQUA), false);
		for (Param param : params) {
			boolean untouched = tuning.isDefault(param);
			String line = "  " + param.id + ": " + WeaponTuning.number(tuning.value(param))
					+ (untouched ? "" : " [" + WeaponTuning.number(tuning.defaultValue(param)) + "]")
					+ "  (" + param.range() + ")";
			source.sendSuccess(() -> Component.literal(line).withStyle(untouched ? ChatFormatting.GRAY : ChatFormatting.WHITE), false);
		}
		return params.size();
	}

	/** One number — or, for the word {@code reset} in the parameter's place, this weapon's whole sheet. */
	private static int tuneParam(CommandSourceStack source, String weaponId, String paramId) {
		Optional<Weapon> found = weaponOr(source, weaponId);
		if (found.isEmpty()) return 0;
		Weapon weapon = found.get();
		WeaponTuning tuning = WeaponTuning.get(weapon);
		if ("reset".equalsIgnoreCase(paramId)) {
			int changed = tuning.changed().size();
			tuning.reset();
			WeaponTuning.save();
			source.sendSuccess(() -> Component.literal(weapon.displayName + " back to its defaults: " + changed + " values")
					.withStyle(ChatFormatting.YELLOW), true);
			return changed;
		}
		Optional<Param> wanted = paramOr(source, weapon, paramId);
		if (wanted.isEmpty()) return 0;
		Param param = wanted.get();
		String line = weapon.commandId() + " " + param.id + ": " + WeaponTuning.number(tuning.value(param))
				+ (tuning.isDefault(param) ? " (default)" : " [default " + WeaponTuning.number(tuning.defaultValue(param)) + "]")
				+ ", " + param.range();
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/**
	 * Move one number and write the file. Reported old → new because a tuning session is a series of
	 * small nudges, and what the number just was is the thing you want back when a nudge went wrong.
	 */
	private static int tuneSet(CommandSourceStack source, String weaponId, String paramId, double value) {
		Optional<Weapon> found = weaponOr(source, weaponId);
		if (found.isEmpty()) return 0;
		Weapon weapon = found.get();
		Optional<Param> wanted = paramOr(source, weapon, paramId);
		if (wanted.isEmpty()) return 0;
		Param param = wanted.get();
		// Several of these are loop bounds and spawn counts, so a number outside the range is refused
		// rather than clamped: silently getting a 4 for the 500 you typed is worse than being told no.
		if (!param.holds(value)) {
			source.sendFailure(Component.literal(param.id + " must be " + param.range() + ", not "
					+ WeaponTuning.number(value)).withStyle(ChatFormatting.RED));
			return 0;
		}
		WeaponTuning tuning = WeaponTuning.get(weapon);
		double was = tuning.set(param, value);
		WeaponTuning.save();
		source.sendSuccess(() -> Component.literal(weapon.commandId() + " " + param.id + ": "
				+ WeaponTuning.number(was) + " → " + WeaponTuning.number(value)
				+ (tuning.isDefault(param) ? " (the default)" : " [default " + WeaponTuning.number(tuning.defaultValue(param)) + "]")), true);
		return 1;
	}

	/** The named weapon, or a failure that says which names there are. */
	private static Optional<Weapon> weaponOr(CommandSourceStack source, String id) {
		Optional<Weapon> weapon = Weapon.byId(id);
		if (weapon.isEmpty()) {
			source.sendFailure(Component.literal("No weapon called \"" + id + "\". Try one of: " + Weapon.idList())
					.withStyle(ChatFormatting.RED));
		}
		return weapon;
	}

	/**
	 * The named parameter of that weapon, or a failure that lists the ones it has. Only the ones it
	 * has: offering the charger's {@code range_full} on the slosher would be offering a number that
	 * nothing reads.
	 */
	private static Optional<Param> paramOr(CommandSourceStack source, Weapon weapon, String id) {
		Optional<Param> param = Param.byId(id).filter(found -> WeaponTuning.applies(weapon, found));
		if (param.isEmpty()) {
			source.sendFailure(Component.literal("The " + weapon.displayName + " has no parameter called \"" + id
					+ "\". Try one of: " + WeaponTuning.paramList(weapon)).withStyle(ChatFormatting.RED));
		}
		return param;
	}

}
