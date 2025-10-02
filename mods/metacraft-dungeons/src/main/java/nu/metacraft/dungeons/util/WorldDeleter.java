package nu.metacraft.dungeons.util;

import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import nu.metacraft.lib.util.TaskScheduler;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.extensions.ServerWorldExtension;
import nu.metacraft.dungeons.mixin.AccessorMinecraftServer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldDeleter {

	protected static void delete(ServerWorld world, Runnable onCompleted, Predicate<Path> filesToNotRemove, Runnable handlePlayers) {
		if (world.getRegistryKey() == ServerWorld.OVERWORLD) return;
		MinecraftServer server = world.getServer();
		world.savingDisabled = true;
		((ServerWorldExtension) world).metacraft$setBeingDeleted(true);

		//This might be in a world tick, if we don't schedule it, we might get a ConcurrentModificationException.
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
				server.execute(() -> {
					if (shouldRestore) {
						var newWorld = new ServerWorld(
								server, ((AccessorMinecraftServer) server).getWorkerExecutor(),
								((AccessorMinecraftServer) server).getSession(),
								new UnmodifiableLevelProperties(
										server.getSaveProperties(), server.getSaveProperties().getMainWorldProperties()
								),
								world.getRegistryKey(),
								server.getCombinedDynamicRegistries().getCombinedRegistryManager().getOrThrow(RegistryKeys.DIMENSION)
										.get(world.getRegistryKey().getValue()),
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
					} else {
						onCompleted.run();
					}
				});
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
				player.putFloat("Health", 0);
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
			Function<NbtCompound, TeleportTarget> targetPos
	) {
		delete(world, onCompleted, filesToNotRemove, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world.getServer(), player -> {
				MutableBoolean modified = new MutableBoolean(false);
				if (DisconnectedPlayerHelper.getPlayerDim(player) == world.getRegistryKey()) {
					TeleportTarget target = targetPos.apply(player);
					DisconnectedPlayerHelper.setDim(player, target.world().getRegistryKey());
					DisconnectedPlayerHelper.modifyPassengersAndRootVehicle(
							player, entity -> {
								DisconnectedPlayerHelper.setPos(entity, target.position());
								DisconnectedPlayerHelper.setVelocity(entity, target.position());
								DisconnectedPlayerHelper.setRotation(entity, target.yaw(), target.pitch());
							}
					);
					modified.setTrue();
				}
				if (DisconnectedPlayerHelper.getSpawnPoint(player).map(dim -> dim.respawnData().getDimension()).orElse(null) == world.getRegistryKey()) {
					DisconnectedPlayerHelper.removeSpawnPoint(player);
					modified.setTrue();
				}
				if (player.contains(ServerPlayerEntity.ENDER_PEARLS_KEY)) {
					var list = player.getListOrEmpty(ServerPlayerEntity.ENDER_PEARLS_KEY);
					list.removeIf(nbt -> {
						if (nbt instanceof NbtCompound pearl) {
							if (DisconnectedPlayerHelper.getEnderPearlDim(pearl) == world.getRegistryKey()) {
								modified.setTrue();
								return true;
							}
						}
						return false;
					});
				}
				return modified.booleanValue();
			});
		});
	}

}
