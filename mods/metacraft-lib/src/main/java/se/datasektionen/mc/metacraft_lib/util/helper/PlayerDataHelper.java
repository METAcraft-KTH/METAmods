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

	/**
	 * Saves the current player data to the given id slot.
	 * @param player The player to save data from.
	 * @param id The id slot within that player to save to.
	 */
	public static void saveCurrentPlayerData(ServerPlayerEntity player, Identifier id) {
		var ext = ext(player);
		ext.metacraft_lib$setPlayerData(id, ext.metacraft_lib$savePlayerDataExceptDataMap());
	}

	/**
	 * Loads the given id slot for the given player.
	 * @param player The player.
	 * @param id The id slot.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 * @param includeVehicleAndPassengers Whether to spawn any passengers/vehicles.
	 * @param includeFarawayEntities Whether to spawn any additional entities such as ender pearls stored in player data.
	 */
	public static void loadPlayerData(
			ServerPlayerEntity player, Identifier id, boolean moveToDataPosition,
			boolean includeVehicleAndPassengers, boolean includeFarawayEntities
	) {
		ext(player).metacraft_lib$getPlayerData(id).ifPresent(data -> {
			applyPlayerData(player, data, moveToDataPosition, includeVehicleAndPassengers, includeFarawayEntities);
		});
	}


	/**
	 * Detaches player from any vehicle/passengers if another player is riding them.
	 * Run this before saving the player data before {@link PlayerDataHelper#unloadPassengersAndVehicles(ServerPlayerEntity)}
	 * or {@link PlayerDataHelper#unloadAllPlayerConnectedEntities(ServerPlayerEntity)}
	 * @param player The player to update.
	 */
	public static void detachPassengersBeforeSaving(ServerPlayerEntity player) {
		if (player.getRootVehicle().getPlayerPassengers() != 1) {
			player.dismountVehicle();
		}
		if (player.getPlayerPassengers() != 0) {
			player.removeAllPassengers();
		}
	}

	/**
	 * Unloads all vehicles the player is riding if no other player is also riding them.
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayerEntity, Identifier)}
	 * they will be lost forever!
	 * Also, make sure to run {@link PlayerDataHelper#detachPassengersBeforeSaving(ServerPlayerEntity)}
	 * before actually saving the data before running this function. Otherwise, you might get duplicate entities when loading!
	 * @param player The player to remove vehicles from.
	 */
	public static void unloadPassengersAndVehicles(ServerPlayerEntity player) {
		var rootVehicle = player.getRootVehicle();
		if (rootVehicle.getPlayerPassengers() == 1) {
			player.dismountVehicle();
			rootVehicle.streamPassengersAndSelf().forEach(entity -> {
				entity.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
			});
		}
	}

	/**
	 * Unloads all entities connected but not directly attached to players (basically just ender pearls).
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayerEntity, Identifier)}
	 * they will be lost forever!
	 * @param player The player to remove vehicle from.
	 */
	public static void unloadFarawayEntities(ServerPlayerEntity player) {
		player.getEnderPearls().forEach(pearl -> pearl.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
	}

	/**
	 * Unloads all entities that should unload when a player disconnects (at least in vanilla).
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayerEntity, Identifier)}
	 * they will be lost forever!
	 * Also, make sure to run {@link PlayerDataHelper#detachPassengersBeforeSaving(ServerPlayerEntity)}
	 * before actually saving the data before running this function. Otherwise, you might get duplicate entities when loading!
	 * @param player The player to remove vehicle from.
	 * @see PlayerDataHelper#unloadPassengersAndVehicles(ServerPlayerEntity)
	 * @see PlayerDataHelper#unloadFarawayEntities(ServerPlayerEntity)
	 */
	public static void unloadAllPlayerConnectedEntities(ServerPlayerEntity player) {
		unloadPassengersAndVehicles(player);
		unloadFarawayEntities(player);
	}

	/**
	 * Resets the player data to nothing. This includes the inventory, ender chest and basically everything.
	 * Note that vehicle and ender pearls are not reset.
	 * To remove them, please use {@link PlayerDataHelper#unloadAllPlayerConnectedEntities(ServerPlayerEntity)}
	 * @param player The player to modify.
	 */
	public static void resetPlayerData(ServerPlayerEntity player) {
		applyPlayerData(player, getEmptyPlayerData(), false, false, false);
	}

	/**
	 * Returns player data that will reset a player.
	 * @return The data.
	 */
	public static NbtCompound getEmptyPlayerData() {
		return CLEAR_PLAYER.copy();
	}

	/**
	 * Gets the world from the given player data.
	 * @param server The server to load the world in.
	 * @param data The data containing a dimension tag.
	 * @return The world or empty if that world did not exist.
	 */
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

	/**
	 * Loads any passengers and root vehicle from the given data and forces them to ride each other.
	 * @param entity The entity to modify.
	 * @param nbt The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 * @see PlayerDataHelper#loadPassengers(LivingEntity, NbtCompound, UnaryOperator)
	 * @see PlayerDataHelper#loadRootVehicle(LivingEntity, NbtCompound, UnaryOperator)
	 */
	public static void loadRootVehicleAndPassengers(LivingEntity entity, NbtCompound nbt, UnaryOperator<Entity> spawner) {
		loadRootVehicle(entity, nbt, spawner);
		loadPassengers(entity, nbt, spawner);
	}

	/**
	 * Loads any passengers from the given data and forces them to ride the provided entity.
	 * @param entity The entity to modify.
	 * @param nbt The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 */
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

	/**
	 * Loads the given root vehicle from the given data and forces the player to ride it.
	 * @param player The player to modify.
	 * @param data The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 */
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

	/**
	 * Reset a player to the provided player data.
	 * Will always try to spawn any entities that may be present in the player data such as vehicles and ender pearls.
	 * @param player The player to apply data to.
	 * @param data The data to apply.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 */
	public static void applyPlayerData(
			ServerPlayerEntity player, NbtCompound data, boolean moveToDataPosition
	) {
		applyPlayerData(player, data, moveToDataPosition, true, true);
	}

	/**
	 * Reset a player to the provided player data.
	 * @param player The player to apply data to.
	 * @param data The data to apply.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 * @param spawnVehicleAndPassengers Whether to spawn any passengers/vehicles.
	 * @param spawnFarawayEntities Whether to spawn any additional entities such as ender pearls stored in player data.
	 */
	public static void applyPlayerData(
			ServerPlayerEntity player, NbtCompound data, boolean moveToDataPosition,
			boolean spawnVehicleAndPassengers, boolean spawnFarawayEntities
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
		}
		if (spawnFarawayEntities) {
			player.readEnderPearls(Optional.of(data));
		}
	}

}
