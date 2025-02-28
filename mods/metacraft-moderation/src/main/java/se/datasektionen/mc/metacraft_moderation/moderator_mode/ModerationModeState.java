package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;
import se.datasektionen.mc.metacraft_moderation.ModerationData;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;
import se.datasektionen.mc.metacraft_moderation.compat.Vanish;

import java.util.*;

public class ModerationModeState {

	public static final ModerationModeState NULL = new ModerationModeState(
		new ModeratorModeDefinition("null", false, true, false, false)
	);

	private static final String DEF = "Definition";
	private static final String PLAYER_NBT = "PlayerNBT";

	protected ModeratorModeDefinition def;
	protected NbtCompound playerNBT;

	public ModerationModeState(ModeratorModeDefinition def) {
		this.def = def;
	}

	private NbtCompound writePlayerToNBT(ServerPlayerEntity player) {
		PlayerDataHelper.detachPassengersBeforeSaving(player);
		NbtCompound nbt = player.writeNbt(new NbtCompound());
		PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
		return nbt;
	}

	public static Identifier getFromDef(ModeratorModeDefinition def) {
		return METAcraftModeration.getID(def.getName().toLowerCase(Locale.ROOT));
	}

	public void applyToPlayer(ModerationModeState prev, ServerPlayerEntity player) {
		if (prev == null) {
			prev = NULL;
		}
		if (this != NULL) {
			this.playerNBT = prev.playerNBT;
		}
		if (prev.def.shouldHaveSeparatePlayerData()) {
			((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().put(prev.def.getName(), writePlayerToNBT(player));
		}

		boolean applyVanishBeforeData = def.vanish;

		if (applyVanishBeforeData) {
			if (IsLoaded.VANISH.isLoaded()) {
				Vanish.setVanishState(player, def.vanish);
			}
		}

		if (def.shouldHaveSeparatePlayerData()) {
			if (!prev.def.shouldHaveSeparatePlayerData()) {
				if (playerNBT != null) {
					METAcraftModeration.LOGGER.fatal("Overwriting player data for " + player + "!" + "Their previous nbt was " + playerNBT.asString() + ". This should not happen!");
				}
				playerNBT = writePlayerToNBT(player);
			}
			NbtCompound newNbt = PlayerDataHelper.getEmptyPlayerData();
			Optional.ofNullable(((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().get(def.getName())).ifPresent(newNbt::copyFrom);
			PlayerDataHelper.applyPlayerData(player, newNbt, false);
			PlayerDataHelper.setAdvancementTracker(player, getFromDef(def), false);
			PlayerDataHelper.setStatHandler(player, getFromDef(def), false);
		} else if (prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
			if (prev.playerNBT != null) {
				PlayerDataHelper.applyPlayerData(player, prev.playerNBT, true);
			} else {
				METAcraftModeration.LOGGER.fatal("Player " + player.getName() + " lost their player data! This is a bug!");
			}
		}

		PlayerDataHelper.setAnnounceAdvancements(player, def.announceAdvancements);

		if (!applyVanishBeforeData) {
			if (IsLoaded.VANISH.isLoaded()) {
				Vanish.setVanishState(player, def.vanish);
			}
		}

		prev.def.getExitCommand().map(command -> command.replaceAll("@s(?= |$)", player.getGameProfile().getName())).ifPresent(exit -> {
			player.getServer().getCommandManager().executeWithPrefix(
					player.getCommandSource().withLevel(4), exit
			);
		});
		def.getEnterCommand().map(command -> command.replaceAll("@s(?= |$)", player.getGameProfile().getName())).ifPresent(enter -> {
			player.getServer().getCommandManager().executeWithPrefix(
					player.getCommandSource().withLevel(4), enter
			);
		});
		updatePlayer(player);
	}

	public void updatePlayer(ServerPlayerEntity player) {
		if (!IsLoaded.VANISH.isLoaded()) {
			player.setInvisible(def.vanish);
		}
	}

	public NbtCompound toNBT() {
		NbtCompound nbt = new NbtCompound();
		if (this.playerNBT != null) {
			nbt.put(PLAYER_NBT, this.playerNBT);
		}
		nbt.putString(DEF, def.name);
		return nbt;
	}

	public void fromNBT(ModerationData data, NbtCompound nbt) {
		if (nbt.contains(PLAYER_NBT)) {
			this.playerNBT = nbt.getCompound(PLAYER_NBT);
		}
		var defName = nbt.getString(DEF).toLowerCase(Locale.ROOT);
		def = data.getDefinition(defName).orElseGet(() -> {
			METAcraftModeration.LOGGER.error("Unable to load moderator definition named " + defName);
			return NULL.def;
		});
	}

	public ModeratorModeDefinition getDef() {
		return def;
	}

	public static ModerationModeState createFromNBT(ModerationData data, NbtCompound nbt) {
		var state = new ModerationModeState(NULL.def);
		state.fromNBT(data, nbt);
		return state;
	}

}
