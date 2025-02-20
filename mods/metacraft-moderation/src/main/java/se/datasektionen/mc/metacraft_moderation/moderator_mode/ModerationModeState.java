package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;
import se.datasektionen.mc.metacraft_moderation.ModerationData;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;
import se.datasektionen.mc.metacraft_moderation.compat.Vanish;
import se.datasektionen.mc.metacraft_moderation.mixin.AccessorPlayerManager;
import se.datasektionen.mc.metacraft_moderation.mixin.AccessorServerPlayerEntity;

import java.util.*;
import java.util.function.Consumer;

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

	private static final NbtCompound CLEAR_PLAYER = new NbtCompound();

	static {
		CLEAR_PLAYER.putBoolean("seenCredits", true);
		CLEAR_PLAYER.put("EnderItems", new NbtList());
		CLEAR_PLAYER.put("ShoulderEntityLeft", new NbtCompound());
		CLEAR_PLAYER.put("ShoulderEntityRight", new NbtCompound());
	}

	private void loadPlayerVehicle(NbtCompound nbtCompound, ServerPlayerEntity player, Consumer<Entity> vehicleModifier) {
		if (nbtCompound.contains("RootVehicle", NbtElement.COMPOUND_TYPE)) {
			var rootVehicleData = nbtCompound.getCompound("RootVehicle");
			var rootVehicleEntity = EntityType.loadEntityWithPassengers(
					rootVehicleData.getCompound("Entity"), player.getServerWorld(), SpawnReason.LOAD, vehicle -> {
						vehicleModifier.accept(vehicle);
						if (!player.getServerWorld().tryLoadEntity(vehicle)) {
							return null;
						}
						return vehicle;
					}
			);
			if (rootVehicleEntity != null) {
				UUID directPlayerVehicle = rootVehicleData.containsUuid("Attach") ? rootVehicleData.getUuid("Attach") : null;
				if (rootVehicleEntity.getUuid().equals(directPlayerVehicle)) {
					player.startRiding(rootVehicleEntity, true);
				} else {
					for (var entity : rootVehicleEntity.getPassengersDeep()) {
						if (entity.getUuid().equals(directPlayerVehicle)) {
							player.startRiding(entity, true);
							break;
						}
					}
				}
				if (!player.hasVehicle()) {
					METAcraftModeration.LOGGER.warn("Couldn't reattach entity to player");
					rootVehicleEntity.discard();
					for (Entity entity : rootVehicleEntity.getPassengersDeep()) {
						entity.discard();
					}
				}
			}
		}
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
			Vec3d pos = player.getPos();
			if (!prev.def.shouldHaveSeparatePlayerData()) {
				if (playerNBT != null) {
					METAcraftModeration.LOGGER.fatal("Overwriting player data for " + player + "!" + "Their previous nbt was " + playerNBT.asString() + ". This should not happen!");
				}
				playerNBT = writePlayerToNBT(player);
			}
			NbtCompound newNbt = CLEAR_PLAYER.copy();
			Optional.ofNullable(((ModerationPlayerData) player).METAcraft_Moderation$getSavedNBT().get(def.getName())).ifPresent(newNbt::copyFrom);
			player.readNbt(newNbt);
			Optional.ofNullable(AccessorServerPlayerEntity.callGameModeFromNbt(newNbt, "playerGameType")).ifPresent(
					player::changeGameMode
			);
			player.setPos(pos.getX(), pos.getY(), pos.getZ());
			player.resetPosition();
			loadPlayerVehicle(
					newNbt, player,
					entity -> entity.refreshPositionAndAngles(
							player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()
					)
			);
		} else if (prev.def.shouldHaveSeparatePlayerData() && !def.shouldHaveSeparatePlayerData()) {
			if (prev.playerNBT != null) {
				player.readNbt(prev.playerNBT);
				ServerWorld world = DimensionType.worldFromDimensionNbt(
						new Dynamic<>(NbtOps.INSTANCE, prev.playerNBT.get("Dimension"))
				).flatMap(key -> {
					var dim = player.getServer().getWorld(key);
					if (dim == null) {
						return DataResult.error(() -> "Dimension " + key + " did not exist.");
					}
					return DataResult.success(dim);
				}).resultOrPartial(METAcraftModeration.LOGGER::error).orElse(player.getServer().getOverworld());
				player.teleport(world, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYaw(), player.getPitch(), false);
				player.changeGameMode(AccessorServerPlayerEntity.callGameModeFromNbt(prev.playerNBT, "playerGameType"));
				loadPlayerVehicle(prev.playerNBT, player, entity -> {});
			} else {
				METAcraftModeration.LOGGER.fatal("Player " + player.getName() + " lost their player data! This is a bug!");
			}
		}

		if (!applyVanishBeforeData) {
			if (IsLoaded.VANISH.isLoaded()) {
				Vanish.setVanishState(player, def.vanish);
			}
		}

		prev.def.getExitCommand().map(command -> command.replaceAll("@s", player.getGameProfile().getName())).ifPresent(exit -> {
			player.getServer().getCommandManager().executeWithPrefix(
					player.getCommandSource().withLevel(4), exit
			);
		});
		def.getEnterCommand().map(command -> command.replaceAll("@s", player.getGameProfile().getName())).ifPresent(enter -> {
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
