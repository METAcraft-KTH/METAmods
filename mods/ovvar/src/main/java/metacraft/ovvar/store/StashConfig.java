package metacraft.ovvar.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code stash} block of {@code config/ovvar.json}: what this server is, and when a player may
 * take a patch out of their stash (onto their ovve). Every key has a default.
 *
 * @param minigameServer	  a minigame server: the stash and every ovve are view-only here — no sewing,
 *							no unpicking, no stand sessions. Patches earned here still go to the stash
 * @param sewGameModes		game modes in which a player may sew from the stash (adventure players cannot)
 * @param ingameObjective	 a scoreboard objective; a player whose score in it is not 0 is in a game and
 *							cannot sew ("" to skip the check)
 * @param bankOnPickup		when a patch item in a player's inventory is banked to their stash and taken
 *							away: {@code minigame} (only on a minigame server, where it would be lost),
 *							{@code always}, or {@code never}
 * @param bankInCreative	  whether that applies to creative-mode players too (off: gamemasters keep
 *							the item, for showcase stands)
 * @param unpickToStash	   an unpicked patch goes to the stash (true) rather than into the hand as an item
 * @param withdraw			whether the stash lets a player take a patch out as an item here (a survival
 *							server; never on a minigame server)
 * @param sessions			whether the private sewing flow exists at all (a posed stand of your own, the patch
 *							pinned in the hotbar); off by default: the stash just hands patches out as items
 * @param stashClick		  with sessions on: what a left-click on a patch in the stash does: {@code withdraw} (the patch into the
 *							hand as an item, to sew on any stand or trade; the vanilla way) or {@code session}
 *							(a private posed stand with the patch pinned in the hotbar). Right-click does the other
 * @param anyStand			whether patches may be sewn and unpicked on any armour stand wearing an ovve
 *							(default true); off: only on the private stand a stash session spawns
 * @param sessionReach		how far (blocks) a player may walk from their session stand before it ends
 * @param sessionSeconds	  how long a session lasts without a sew or unpick before it ends
 * @param explainInChat	   send the "what the stash is" lines when a patch is earned
 */
public record StashConfig(
		boolean minigameServer, List<GameType> sewGameModes, String ingameObjective, Bank bankOnPickup, boolean bankInCreative,
		boolean unpickToStash, boolean withdraw, boolean sessions, StashClick stashClick, boolean anyStand, double sessionReach, int sessionSeconds, boolean explainInChat
) {
	public enum StashClick implements net.minecraft.util.StringRepresentable {
		WITHDRAW("withdraw"), SESSION("session");

		public static final Codec<StashClick> CODEC = net.minecraft.util.StringRepresentable.fromEnum(StashClick::values);
		private final String name;

		StashClick(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public enum Bank implements net.minecraft.util.StringRepresentable {
		MINIGAME("minigame"), ALWAYS("always"), NEVER("never");

		public static final Codec<Bank> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Bank::values);
		private final String name;

		Bank(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Are patch items banked on this server? */
	public boolean banksOnPickup() {
		return switch (bankOnPickup) {
			case MINIGAME -> minigameServer;
			case ALWAYS -> true;
			case NEVER -> false;
		};
	}

	/** May a player take a patch out of the stash as an item here? */
	public boolean canWithdraw() {
		return withdraw && !minigameServer;
	}

	// ---- why not, in words: the wardrobe screen shows an unavailable action greyed out with its reason

	/** What a minigame server is, said once, wherever an action is refused for being on one. */
	public static final String LOOK_ONLY = "Minigame server: look only";

	/** Why a patch cannot be taken out of the stash here, or null if it can. */
	public @Nullable String whyNoWithdraw() {
		if (minigameServer) return LOOK_ONLY;
		return withdraw ? null : "This server does not hand patches out";
	}

	/** Why a sewing session cannot be started here, or null if it can. */
	public @Nullable String whyNoSessions() {
		if (minigameServer) return LOOK_ONLY;
		return sessions ? null : "Sewing sessions are off on this server";
	}

	/** Why held patch items cannot be banked here, or null if they can. */
	public @Nullable String whyNoDeposit() {
		return minigameServer && !banksOnPickup() ? LOOK_ONLY : null;
	}

	/** Why a mannequin cannot be shown here, or null if one can. */
	public @Nullable String whyNoMannequin() {
		return minigameServer ? LOOK_ONLY : null;
	}

	private static final Codec<GameType> GAME_TYPE = Codec.STRING.comapFlatMap(
			s -> {
				GameType type = GameType.byName(s, null);
				return type == null ? com.mojang.serialization.DataResult.error(() -> "unknown game mode " + s) : com.mojang.serialization.DataResult.success(type);
			},
			GameType::getName);

	/** Written into the file as {@code _help}, one line per key, since JSON has no comments. */
	public static final Map<String, String> HELP = new LinkedHashMap<>();
	static {
		HELP.put("_about", "What this server is, and the rules for patches here. Patches live in a player's stash (shared by all servers), on their ovve, or as items in the world.");
		HELP.put("minigame_server", "true on a minigame server: the stash can be looked at but nothing sewn, unpicked or taken out, and any patch item that lands in an inventory is banked into the stash so it cannot be lost to a locked or wiped inventory. false on a survival server.");
		HELP.put("sew_game_modes", "Game modes in which a player may sew, unpick and take patches out of the stash. Adventure is deliberately not one.");
		HELP.put("ingame_objective", "A scoreboard objective. A player whose score in it is not 0 is in a game and may not sew or take patches out. \"\" turns the check off.");
		HELP.put("bank_on_pickup", "When a patch item in a player's inventory is moved into their stash automatically: \"minigame\" (only on a minigame server), \"always\", or \"never\".");
		HELP.put("bank_in_creative", "Whether that also happens to players in creative mode. false lets gamemasters keep patch items for showcase stands.");
		HELP.put("unpick_to_stash", "true: a patch unpicked from a stand goes to the stash. false: it goes into the hand as an item, the vanilla feel.");
		HELP.put("withdraw", "Whether a player may take a patch out of the stash as an item here (to sew on a stand or trade). Never on a minigame server.");
		HELP.put("sessions", "The private sewing flow: click a patch in the stash to get your own posed armour stand with the patch pinned in hotbar slot 9 and shears in slot 8. Off by default; the stash then just hands patches out as items.");
		HELP.put("stash_click", "With sessions on: what a left-click on a patch in the stash does, \"withdraw\" (take it out as an item) or \"session\". Right-click does the other.");
		HELP.put("any_stand", "Patches may be sewn and unpicked on any armour stand wearing an owned ovve (true, the classic way). false: only on a session stand.");
		HELP.put("session_reach", "Blocks a player may walk from their session stand before the session ends.");
		HELP.put("session_seconds", "Idle seconds before a session ends.");
		HELP.put("explain_in_chat", "When a patch is earned, also explain in chat what the stash is and how to use it.");
	}

	public static final StashConfig DEFAULT = new StashConfig(false, List.of(GameType.SURVIVAL, GameType.CREATIVE), "ingame",
			Bank.MINIGAME, false, false, true, false, StashClick.WITHDRAW, true, 8.0, 300, true);

	public static final MapCodec<StashConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("_help", java.util.Map.of()).forGetter(c -> HELP),
			Codec.BOOL.optionalFieldOf("minigame_server", DEFAULT.minigameServer).forGetter(StashConfig::minigameServer),
			GAME_TYPE.listOf().optionalFieldOf("sew_game_modes", DEFAULT.sewGameModes).forGetter(StashConfig::sewGameModes),
			Codec.STRING.optionalFieldOf("ingame_objective", DEFAULT.ingameObjective).forGetter(StashConfig::ingameObjective),
			Bank.CODEC.optionalFieldOf("bank_on_pickup", DEFAULT.bankOnPickup).forGetter(StashConfig::bankOnPickup),
			Codec.BOOL.optionalFieldOf("bank_in_creative", DEFAULT.bankInCreative).forGetter(StashConfig::bankInCreative),
			Codec.BOOL.optionalFieldOf("unpick_to_stash", DEFAULT.unpickToStash).forGetter(StashConfig::unpickToStash),
			Codec.BOOL.optionalFieldOf("withdraw", DEFAULT.withdraw).forGetter(StashConfig::withdraw),
			Codec.BOOL.optionalFieldOf("sessions", DEFAULT.sessions).forGetter(StashConfig::sessions),
			StashClick.CODEC.optionalFieldOf("stash_click", DEFAULT.stashClick).forGetter(StashConfig::stashClick),
			Codec.BOOL.optionalFieldOf("any_stand", DEFAULT.anyStand).forGetter(StashConfig::anyStand),
			Codec.doubleRange(1, 64).optionalFieldOf("session_reach", DEFAULT.sessionReach).forGetter(StashConfig::sessionReach),
			Codec.intRange(10, 3600).optionalFieldOf("session_seconds", DEFAULT.sessionSeconds).forGetter(StashConfig::sessionSeconds),
			Codec.BOOL.optionalFieldOf("explain_in_chat", DEFAULT.explainInChat).forGetter(StashConfig::explainInChat)
	).apply(instance, (help, minigame, modes, objective, bank, creative, unpickToStash, withdraw, sessions, click, anyStand, reach, seconds, explain) ->
			new StashConfig(minigame, modes, objective, bank, creative, unpickToStash, withdraw, sessions, click, anyStand, reach, seconds, explain)));
}
