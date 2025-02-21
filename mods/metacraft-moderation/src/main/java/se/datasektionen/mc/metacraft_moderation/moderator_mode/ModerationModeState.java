package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;
import se.datasektionen.mc.metacraft_moderation.ModerationData;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;
import se.datasektionen.mc.metacraft_moderation.compat.Vanish;
import se.datasektionen.mc.metacraft_moderation.mixin.AccessorPlayerManager;
import se.datasektionen.mc.metacraft_moderation.mixin.AccessorServerPlayerEntity;

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
		boolean hasOtherPlayerRider = false;
		for (var entity : player.getRootVehicle().getPassengersDeep()) {
			if (entity instanceof PlayerEntity && entity != player) {
				hasOtherPlayerRider = true;
				break;
			}
		}
		if (hasOtherPlayerRider) {
			player.dismountVehicle();
		}
		NbtCompound nbt = player.writeNbt(new NbtCompound());

		if (!hasOtherPlayerRider && player.hasVehicle()) {
			List<Entity> toRemove = new ArrayList<>();
			for (var entity : player.getRootVehicle().getPassengersDeep()) {
				if (entity != player) {
					toRemove.add(entity);
				}
			}
			toRemove.add(player.getRootVehicle());
			for (var entity : toRemove) {
				if (entity instanceof Inventory inv) {
					inv.clear();
				}
				entity.discard();
			}
		}
		return nbt;
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
		} else if (prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
			if (prev.playerNBT != null) {
				PlayerDataHelper.applyPlayerData(player, prev.playerNBT, true);
			} else {
				METAcraftModeration.LOGGER.fatal("Player " + player.getName() + " lost their player data! This is a bug!");
			}
		}

		if (!applyVanishBeforeData) {
			if (IsLoaded.VANISH.isLoaded()) {
				Vanish.setVanishState(player, def.vanish);
			}
		}

		prev.def.getExitCommand().map(command -> command.replaceAll("@s(?= )", player.getGameProfile().getName())).ifPresent(exit -> {
			player.getServer().getCommandManager().executeWithPrefix(
					player.getCommandSource().withLevel(4), exit
			);
		});
		def.getEnterCommand().map(command -> command.replaceAll("@s(?= )", player.getGameProfile().getName())).ifPresent(enter -> {
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
		if (def.shouldHaveSeparatePlayerData()) {
			player.getAdvancementTracker().save();
			player.getStatHandler().save();
			var playerManager = player.getServer().getPlayerManager();
			((AccessorServerPlayerEntity) player).setAdvancementTracker(
					new ModAdvancementTracker(
							player.getServer().getDataFixer(), playerManager,
							player.getServer().getAdvancementLoader(), player, def
					)
			);
			((AccessorPlayerManager) playerManager).getAdvancementTrackers().put(
					player.getUuid(), player.getAdvancementTracker()
			);
			((AccessorServerPlayerEntity) player).setStatHandler(
					new ModStatHandler(player.server, player, def)
			);
			((AccessorPlayerManager) playerManager).getStatisticsMap().put(
					player.getUuid(), player.getStatHandler()
			);
		} else {
			var playerManager = player.getServer().getPlayerManager();
			if (player.getAdvancementTracker() instanceof ModAdvancementTracker t) {
				t.save();
				((AccessorPlayerManager) playerManager).getAdvancementTrackers().remove(player.getUuid());
				((AccessorServerPlayerEntity) player).setAdvancementTracker(playerManager.getAdvancementTracker(player));
			}
			if (player.getStatHandler() instanceof ModStatHandler t) {
				t.save();
				((AccessorPlayerManager) playerManager).getStatisticsMap().remove(player.getUuid());
				((AccessorServerPlayerEntity) player).setStatHandler(playerManager.createStatHandler(player));
			}
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
		var defName = nbt.getString(DEF);
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
