package nu.metacraft.moderation.moderator_mode;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.PlayerDataHelper;
import nu.metacraft.moderation.METAcraftModeration;
import nu.metacraft.moderation.ModerationData;
import nu.metacraft.moderation.ModerationPlayerData;
import nu.metacraft.moderation.compat.Vanish;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class ModerationModeState {

	public static final ModerationModeState NULL = new ModerationModeState(
		new ModeratorModeDefinition("null", false, true, false, false)
	);

	private static final String DEF = "definition";
	private static final String PLAYER_NBT = "player_data";

	protected ModeratorModeDefinition def;
	protected CompoundTag playerNBT;

	public ModerationModeState(ModeratorModeDefinition def) {
		this.def = def;
	}

	private CompoundTag writePlayerToNBT(ServerPlayer player, ProblemReporter logger) {
		PlayerDataHelper.detachPassengersBeforeSaving(player);
		var writeView = TagValueOutput.createWithContext(logger, player.registryAccess());
		player.saveWithoutId(writeView);
		PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
		return writeView.buildResult();
	}

	public static Identifier getFromDef(ModeratorModeDefinition def) {
		return METAcraftModeration.getID(def.getName().toLowerCase(Locale.ROOT));
	}

	public void applyToPlayer(ModerationModeState prev, ServerPlayer player) {
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
				CompoundTag newNbt = PlayerDataHelper.getEmptyPlayerData();
				Optional.ofNullable(((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().get(def.getName())).ifPresent(newNbt::merge);
				var readView = TagValueInput.create(logging, player.registryAccess(), newNbt);
				PlayerDataHelper.applyPlayerData(player, readView, false);
				PlayerDataHelper.setAdvancementTracker(player, getFromDef(def), false);
				PlayerDataHelper.setStatHandler(player, getFromDef(def), false);
				if (!def.announceAdvancements) {
					PlayerDataHelper.setAnnounceAdvancements(player, false);
				}
			} else if (prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
				if (prev.playerNBT != null) {
					var readView = TagValueInput.create(logging, player.registryAccess(), PlayerDataHelper.updatePlayerData(prev.playerNBT, player.level().getServer().getFixerUpper()));
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


		prev.def.getExitCommand().map(command -> command.replaceAll("@s(?= |$)", player.getGameProfile().name())).ifPresent(exit -> {
			player.level().getServer().getCommands().performPrefixedCommand(
					player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), exit
			);
		});
		def.getEnterCommand().map(command -> command.replaceAll("@s(?= |$)", player.getGameProfile().name())).ifPresent(enter -> {
			player.level().getServer().getCommands().performPrefixedCommand(
					player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), enter
			);
		});
		updatePlayer(player);
	}

	public void updatePlayer(ServerPlayer player) {
		if (!IsLoaded.VANISH.isLoaded()) {
			player.setInvisible(def.vanish);
		}
	}

	public CompoundTag toNBT() {
		CompoundTag nbt = new CompoundTag();
		if (this.playerNBT != null) {
			nbt.put(PLAYER_NBT, this.playerNBT);
		}
		nbt.putString(DEF, def.name);
		return nbt;
	}

	public void fromNBT(ModerationData data, CompoundTag nbt) {
		if (nbt.contains(PLAYER_NBT)) {
			this.playerNBT = nbt.getCompound(PLAYER_NBT).orElse(null);
		} else {
			this.playerNBT = nbt.getCompound("PlayerNBT").orElse(null);
		}
		Optional<String> defName;
		if (nbt.contains(DEF)) {
			defName = nbt.getString(DEF).map(
					name -> name.toLowerCase(Locale.ROOT)
			);
		} else {
			defName = nbt.getString("Definition").map(
					name -> name.toLowerCase(Locale.ROOT)
			);
		}
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

	public static ModerationModeState createFromNBT(ModerationData data, CompoundTag nbt) {
		var state = new ModerationModeState(NULL.def);
		state.fromNBT(data, nbt);
		return state;
	}

}
