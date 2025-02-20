package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.dimension.DimensionType;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerPlayerEntity;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class PlayerHelper {

	public static boolean shouldShowInGUI(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).METAcraft_Moderation$showInGUI() && !VanishHelper.isVanished(player);
	}



	private static final NbtCompound CLEAR_PLAYER = new NbtCompound();

	static {
		CLEAR_PLAYER.putBoolean("seenCredits", true);
		CLEAR_PLAYER.put("EnderItems", new NbtList());
		CLEAR_PLAYER.put("ShoulderEntityLeft", new NbtCompound());
		CLEAR_PLAYER.put("ShoulderEntityRight", new NbtCompound());
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

	public static void loadRootVehicle(LivingEntity player, NbtCompound data, Consumer<Entity> spawner) {
		if (data.contains("RootVehicle")) {
			var vehicle = data.getCompound("RootVehicle");
			var e = EntityType.loadEntityWithPassengers(vehicle.getCompound("Entity"), player.getWorld(), SpawnReason.LOAD, entity -> {
				spawner.accept(entity);
				return entity;
			});
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
		var prevPos = player.getPos();
		var prevYaw = player.getYaw();
		var prevPitch = player.getPitch();
		Vec3d prevVelocity = player.getVelocity();

		var prevVehiclePos = player.getRootVehicle().getPos();
		var prevVehicleYaw = player.getRootVehicle().getYaw();
		var prevVehiclePitch = player.getRootVehicle().getPitch();
		var prevVehicleVelocity = player.getRootVehicle().getVelocity();

		player.readNbt(data);
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
		loadRootVehicle(player, data, e -> {
			if (!moveToDataPosition) {
				e.setPos(prevVehiclePos.x, prevVehiclePos.y, prevVehiclePos.z);
				e.setYaw(prevVehicleYaw);
				e.setPitch(prevVehiclePitch);
				e.setVelocity(prevVehicleVelocity);
			}
			player.getWorld().spawnEntity(e);
		});
	}

}
