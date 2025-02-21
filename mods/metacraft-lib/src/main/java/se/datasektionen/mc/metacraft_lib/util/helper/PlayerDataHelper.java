package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.dimension.DimensionType;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerPlayerEntity;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class PlayerDataHelper {

	private static final NbtCompound CLEAR_PLAYER = new NbtCompound();

	static {
		CLEAR_PLAYER.putBoolean("seenCredits", true);
		CLEAR_PLAYER.put("EnderItems", new NbtList());
		CLEAR_PLAYER.put("ShoulderEntityLeft", new NbtCompound());
		CLEAR_PLAYER.put("ShoulderEntityRight", new NbtCompound());
	}

	private static ServerPlayerEntityExtensions ext(ServerPlayerEntity player) {
		return (ServerPlayerEntityExtensions) player;
	}

	public static void saveCurrentPlayerData(ServerPlayerEntity player, Identifier id) {
		var ext = ext(player);
		ext.metacraft_lib$setPlayerData(id, ext.metacraft_lib$savePlayerDataExceptDataMap());
	}

	public static void loadPlayerData(ServerPlayerEntity player, Identifier id, boolean moveToDataPosition, boolean includeVehicleAndPassengers) {
		ext(player).metacraft_lib$getPlayerData(id).ifPresent(data -> {
			applyPlayerData(player, data, moveToDataPosition, includeVehicleAndPassengers);
		});
	}

	/**
	 * Unloads all vehicles the player is riding if no other player is also riding them.
	 * WARNING: If they have not been saved (using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayerEntity, Identifier)}
	 * they will be lost forever!
	 * @param player The player to remove vehicle from.
	 */
	public static void unloadPlayerConnectedEntities(ServerPlayerEntity player) {
		var rootVehicle = player.getRootVehicle();
		if (rootVehicle.getPlayerPassengers() == 1) {
			rootVehicle.streamPassengersAndSelf().forEach(entity -> {
				if (entity != player) {
					entity.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
				}
			});
			player.getEnderPearls().forEach(pearl -> pearl.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
		}
	}

	public static void resetPlayerData(ServerPlayerEntity player) {
		applyPlayerData(player, getEmptyPlayerData(), false, false);
	}

	public static NbtCompound getEmptyPlayerData() {
		return CLEAR_PLAYER.copy();
	}

	public static Optional<ServerWorld> getWorld(MinecraftServer server, NbtCompound data) {
		return DimensionType.worldFromDimensionNbt(
				new Dynamic<>(NbtOps.INSTANCE, data.get("Dimension"))
		).flatMap(key -> {
			var dim = server.getWorld(key);
			if (dim == null) {
				return DataResult.error(() -> "Dimension " + key + " did not exist.");
			}
			return DataResult.success(dim);
		}).resultOrPartial(METAcraftLib.LOGGER::error);
	}

	public static void loadRootVehicleAndPassengers(LivingEntity entity, NbtCompound nbt, UnaryOperator<Entity> spawner) {
		loadRootVehicle(entity, nbt, spawner);
		loadPassengers(entity, nbt, spawner);
	}

	public static void loadPassengers(LivingEntity entity, NbtCompound nbt, UnaryOperator<Entity> spawner) {
		if (nbt.contains(Entity.PASSENGERS_KEY, NbtElement.LIST_TYPE)) {
			NbtList nbtList = nbt.getList(Entity.PASSENGERS_KEY, NbtElement.COMPOUND_TYPE);

			for (int i = 0; i < nbtList.size(); i++) {
				Entity entity2 = EntityType.loadEntityWithPassengers(
						nbtList.getCompound(i), entity.getWorld(), SpawnReason.LOAD, spawner
				);
				if (entity2 != null) {
					entity2.startRiding(entity, true);
				}
			}
		}
	}

	public static void loadRootVehicle(LivingEntity player, NbtCompound data, UnaryOperator<Entity> spawner) {
		if (data.contains("RootVehicle")) {
			var vehicle = data.getCompound("RootVehicle");
			var e = EntityType.loadEntityWithPassengers(vehicle.getCompound("Entity"), player.getWorld(), SpawnReason.LOAD, spawner);
			if (e != null) {
				Runnable clearEntity = () -> {
					e.streamPassengersAndSelf().forEach(Entity::discard);
					METAcraftLib.LOGGER.error("Unable to reattach player to entity.");
				};
				if (vehicle.containsUuid("Attach")) {
					var id = vehicle.getUuid("Attach");
					for (var entity : (Iterable<Entity>) e.streamSelfAndPassengers()::iterator) {
						if (entity.getUuid().equals(id)) {
							player.startRiding(entity, true);
						}
					}
					if (!player.hasVehicle()) {
						clearEntity.run();
					}
				} else {
					clearEntity.run();
				}
			}
		}
	}

	public static void applyPlayerData(
			ServerPlayerEntity player, NbtCompound data, boolean moveToDataPosition
	) {
		applyPlayerData(player, data, moveToDataPosition, true);
	}

	public static void applyPlayerData(
			ServerPlayerEntity player, NbtCompound data, boolean moveToDataPosition, boolean spawnVehicleAndPassengers
	) {
		var prevPos = player.getPos();
		var prevYaw = player.getYaw();
		var prevPitch = player.getPitch();
		Vec3d prevVelocity = player.getVelocity();

		var prevVehiclePos = player.getRootVehicle().getPos();
		var prevVehicleYaw = player.getRootVehicle().getYaw();
		var prevVehiclePitch = player.getRootVehicle().getPitch();
		var prevVehicleVelocity = player.getRootVehicle().getVelocity();

		ext(player).metacraft_lib$loadPlayerDataExceptDataMap(data);
		Optional<ServerWorld> world = getWorld(player.getServer(), data);
		if (moveToDataPosition) {
			world.ifPresentOrElse(w -> {
				player.teleport(w, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYaw(), player.getPitch(), false);
				player.velocityModified = true;
			}, () -> {
				player.teleportTo(player.getRespawnTarget(true, TeleportTarget.NO_OP));
			});
		} else {
			player.setPos(prevPos.getX(), prevPos.getY(), prevPos.getZ());
			player.setYaw(prevYaw);
			player.setPitch(prevPitch);
			player.setVelocity(prevVelocity);
		}
		var gameMode = AccessorServerPlayerEntity.callGameModeFromNbt(data, "playerGameType");
		if (gameMode != null) {
			player.changeGameMode(gameMode);
		}
		if (spawnVehicleAndPassengers) {
			loadRootVehicleAndPassengers(player, data, e -> {
				if (!moveToDataPosition) {
					e.setPos(prevVehiclePos.x, prevVehiclePos.y, prevVehiclePos.z);
					e.setYaw(prevVehicleYaw);
					e.setPitch(prevVehiclePitch);
					e.setVelocity(prevVehicleVelocity);
				}
				if (!player.getWorld().spawnEntity(e)) {
					return null;
				}
				return e;
			});
			player.readEnderPearls(Optional.of(data));
		}
	}

}
