package se.datasektionen.mc.metacraft_dungeons.util;

import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorMinecraftServer;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorServerChunkLoadingManager;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.function.Function;

public class WorldDeleter {

	protected static void delete(ServerWorld world, Runnable onCompleted, Runnable handlePlayers) {
		if (world.getRegistryKey() == ServerWorld.OVERWORLD) return;
		MinecraftServer server = world.getServer();
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
				deleteFiles(
						((AccessorMinecraftServer) server).getSession().getWorldDirectory(world.getRegistryKey())
				);
				if (shouldRestore) {
					TaskScheduler.scheduleImmediately(world.getServer(), () -> {
						var newWorld = new ServerWorld(
								server, ((AccessorMinecraftServer) server).getWorkerExecutor(),
								((AccessorMinecraftServer) server).getSession(),
								new UnmodifiableLevelProperties(
										server.getSaveProperties(), server.getSaveProperties().getMainWorldProperties()
								),
								world.getRegistryKey(),
								server.getCombinedDynamicRegistries().getCombinedRegistryManager().get(RegistryKeys.DIMENSION)
										.get(world.getRegistryKey().getValue()),
								((AccessorServerChunkLoadingManager) world.getChunkManager().chunkLoadingManager)
										.getWorldGenerationProgressListener(),
								server.getSaveProperties().isDebugWorld(),
								BiomeAccess.hashSeed(server.getSaveProperties().getGeneratorOptions().getSeed()),
								ImmutableList.of(), false, server.getOverworld().getRandomSequences()
						);
						((AccessorMinecraftServer) server).getWorlds().put(
								world.getRegistryKey(),
								newWorld
						);
						ServerWorldEvents.LOAD.invoker().onWorldLoad(server, newWorld);
					});
				}

				onCompleted.run();
			});
			deleterThread.start();
		});
	}

	private static void deleteFiles(Path toDelete) {
		try {
			Files.walkFileTree(toDelete, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path pathx, BasicFileAttributes basicFileAttributes) throws IOException {
					Files.delete(pathx);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path pathx, IOException iOException) throws IOException {
					if (iOException != null) {
						throw iOException;
					} else {
						Files.delete(pathx);
						return FileVisitResult.CONTINUE;
					}
				}
			});
			toDelete.toFile().delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void deleteWorldKillingPlayers(ServerWorld world, Runnable onCompleted) {
		delete(world, onCompleted, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world, player -> {
				player.kill();
				return true;
			});
		});
	}

	public static void deleteWorldTeleportingPlayers(
			ServerWorld world, Runnable onCompleted,
			TeleportTarget targetPos
	) {
		deleteWorldTeleportingPlayers(world, onCompleted, player -> targetPos);
	}

	public static void deleteWorldTeleportingPlayers(
			ServerWorld world, Runnable onCompleted,
			Function<ServerPlayerEntity, TeleportTarget> targetPos
	) {
		delete(world, onCompleted, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world, player -> {
				TeleportTarget target = targetPos.apply(player);
				player.setServerWorld(target.world()); //We don't teleport players who are not online because we don't want to crash the game. We just set the values instead.
				player.setPos(target.pos().x, target.pos().y, target.pos().z);
				player.setVelocity(target.velocity());
				player.setYaw(target.yaw());
				player.setPitch(target.pitch());
				return true;
			});
		});
	}

}
