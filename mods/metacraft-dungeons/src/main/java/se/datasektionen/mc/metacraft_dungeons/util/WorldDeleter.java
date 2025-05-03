package se.datasektionen.mc.metacraft_dungeons.util;

import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerWorldExtension;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorMinecraftServer;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;
import se.datasektionen.mc.metacraft_lib.util.helper.WorldHelper;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldDeleter {

	protected static void delete(ServerWorld world, Runnable onCompleted, Predicate<Path> filesToNotRemove, Runnable handlePlayers) {
		if (world.getRegistryKey() == ServerWorld.OVERWORLD) return;
		MinecraftServer server = world.getServer();
		world.savingDisabled = true;
		((ServerWorldExtension) world).metacraft$setBeingDeleted(true);
		TaskScheduler.scheduleImmediately(world.getServer(), () -> {
			boolean shouldRestore;
			if (server.getWorld(world.getRegistryKey()) != null) {
				shouldRestore = true;
				for (var player : new ArrayList<>(world.getPlayers())) {
					player.networkHandler.disconnect(Text.literal("This dimension is being reset"));
				}
				handlePlayers.run();
				((AccessorMinecraftServer) server).getWorlds().remove(world.getRegistryKey());
				ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, world);
			} else {
				shouldRestore = false;
			}
			Thread deleterThread = new Thread(() -> {
				METAcraftDungeons.LOGGER.info("Unloading world (this might take a while).");
				try {
					world.close();
				} catch (IOException ignored) {}
				METAcraftDungeons.LOGGER.info("World unloaded, deleting files.");
				deleteFiles(
						((AccessorMinecraftServer) server).getSession().getWorldDirectory(world.getRegistryKey()),
						filesToNotRemove
				);
				if (world.getServer().isStopping()) {
					return;
				}
				if (shouldRestore) {
					TaskScheduler.scheduleImmediately(world.getServer(), () -> {
						var newWorld = new ServerWorld(
								server, ((AccessorMinecraftServer) server).getWorkerExecutor(),
								((AccessorMinecraftServer) server).getSession(),
								new UnmodifiableLevelProperties(
										server.getSaveProperties(), server.getSaveProperties().getMainWorldProperties()
								),
								world.getRegistryKey(),
								server.getCombinedDynamicRegistries().getCombinedRegistryManager().getOrThrow(RegistryKeys.DIMENSION)
										.get(world.getRegistryKey().getValue()),
								WorldHelper.getGenerationProgressListener(world),
								server.getSaveProperties().isDebugWorld(),
								BiomeAccess.hashSeed(server.getSaveProperties().getGeneratorOptions().getSeed()),
								ImmutableList.of(), false, server.getOverworld().getRandomSequences()
						);
						((AccessorMinecraftServer) server).getWorlds().put(
								world.getRegistryKey(),
								newWorld
						);
						ServerWorldEvents.LOAD.invoker().onWorldLoad(server, newWorld);
						onCompleted.run();
					});
				} else {
					onCompleted.run();
				}
			});
			deleterThread.start();
		});
	}

	private static void deleteFiles(Path toDelete, Predicate<Path> toKeep) {
		try {
			final Set<Path> foldersToKeep = new HashSet<>();
			Files.walkFileTree(toDelete, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult visitFile(Path pathx, BasicFileAttributes basicFileAttributes) throws IOException {
					if (toKeep.test(pathx)) {
						foldersToKeep.add(pathx.getParent());
					} else {
						Files.delete(pathx);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path pathx, IOException iOException) throws IOException {
					if (iOException != null) {
						throw iOException;
					} else {
						if (toKeep.test(pathx) || foldersToKeep.contains(pathx)) {
							foldersToKeep.add(pathx.getParent());
						} else {
							Files.delete(pathx);
						}
						return FileVisitResult.CONTINUE;
					}
				}
			});
			if (foldersToKeep.isEmpty()) {
				toDelete.toFile().delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void deleteWorldKillingPlayers(ServerWorld world, Runnable onCompleted, Predicate<Path> filesToNotRemove) {
		delete(world, onCompleted, filesToNotRemove, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world, player -> {
				player.kill(world);
				return true;
			});
		});
	}

	public static void deleteWorldTeleportingPlayers(
			ServerWorld world, Runnable onCompleted, Predicate<Path> filesToNotRemove,
			TeleportTarget targetPos
	) {
		deleteWorldTeleportingPlayers(world, onCompleted, filesToNotRemove, player -> targetPos);
	}

	public static void deleteWorldTeleportingPlayers(
			ServerWorld world, Runnable onCompleted, Predicate<Path> filesToNotRemove,
			Function<ServerPlayerEntity, TeleportTarget> targetPos
	) {
		delete(world, onCompleted, filesToNotRemove, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world.getServer(), player -> {
				boolean modified = false;
				if (player.getWorld() == world) {
					TeleportTarget target = targetPos.apply(player);
					player.setServerWorld(target.world()); //We don't teleport players who are not online because we don't want to crash the game. We just set the values instead.
					player.getRootVehicle().streamSelfAndPassengers().forEach(entity -> {
						entity.setPos(target.position().x, target.position().y, target.position().z);
						entity.setVelocity(target.velocity());
						entity.setYaw(target.yaw());
						entity.setPitch(target.pitch());
					});
					modified = true;
				}
				List<EnderPearlEntity> toRemove = new ArrayList<>();
				for (var pearl : player.getEnderPearls()) {
					if (pearl.getWorld() == world) {
						toRemove.add(pearl);
					}
				}
				if (!toRemove.isEmpty()) {
					toRemove.forEach(player.getEnderPearls()::remove);
					modified = true;
				}
				return modified;
			});
		});
	}

}
