package nu.metacraft.moderation.moderator_mode;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.PlayerDataHelper;
import nu.metacraft.moderation.METAcraftModeration;
import nu.metacraft.moderation.ModerationData;
import nu.metacraft.moderation.ModerationPlayerData;
import nu.metacraft.moderation.compat.Vanish;

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

	private NbtCompound writePlayerToNBT(ServerPlayerEntity player, ErrorReporter logger) {
		PlayerDataHelper.detachPassengersBeforeSaving(player);
		var writeView = NbtWriteView.create(logger, player.getRegistryManager());
		player.writeData(writeView);
		PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
		return writeView.getNbt();
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

		try (var logging = LoggingErrorReporter.create(() -> "metacraft:ModerationModeState#applyToPlayer", METAcraftModeration.LOGGER)) {
			if (prev.def.shouldHaveSeparatePlayerData()) {
				((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().put(prev.def.getName(), writePlayerToNBT(player, logging));
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
					playerNBT = writePlayerToNBT(player, logging);
				}
				NbtCompound newNbt = PlayerDataHelper.getEmptyPlayerData();
				Optional.ofNullable(((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().get(def.getName())).ifPresent(newNbt::copyFrom);
				var readView = NbtReadView.create(logging, player.getRegistryManager(), newNbt);
				PlayerDataHelper.applyPlayerData(player, readView, false);
				PlayerDataHelper.setAdvancementTracker(player, getFromDef(def), false);
				PlayerDataHelper.setStatHandler(player, getFromDef(def), false);
				if (!def.announceAdvancements) {
					PlayerDataHelper.setAnnounceAdvancements(player, false);
				}
			} else if (prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
				if (prev.playerNBT != null) {
					var readView = NbtReadView.create(logging, player.getRegistryManager(), PlayerDataHelper.updatePlayerData(prev.playerNBT, player.getServer().getDataFixer()));
					PlayerDataHelper.applyPlayerData(player, readView, true);
				} else {
					METAcraftModeration.LOGGER.fatal("Player " + player.getName() + " lost their player data! This is a bug!");
				}
			}

			if (!prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
				PlayerDataHelper.setAnnounceAdvancements(player, def.announceAdvancements);
			}

			if (!applyVanishBeforeData) {
				if (IsLoaded.VANISH.isLoaded()) {
					Vanish.setVanishState(player, def.vanish);
				}
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
		this.playerNBT = nbt.getCompound(PLAYER_NBT).orElse(null);
		var defName = nbt.getString(DEF).map(
				name -> name.toLowerCase(Locale.ROOT)
		);
		def = defName.flatMap(
				data::getDefinition
		).orElseGet(() -> {
			METAcraftModeration.LOGGER.error("Unable to load moderator definition named " + defName.orElse(null));
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
