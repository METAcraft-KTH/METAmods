package nu.metacraft.dungeons.util;

import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import nu.metacraft.lib.util.TaskScheduler;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.extensions.ServerLevelExtension;
import nu.metacraft.dungeons.mixin.MinecraftServerAccessor;

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

	protected static void delete(ServerLevel world, Runnable onCompleted, Predicate<Path> filesToNotRemove, Runnable handlePlayers) {
		if (world.dimension() == ServerLevel.OVERWORLD) return;
		MinecraftServer server = world.getServer();
		world.noSave = true;
		((ServerLevelExtension) world).metacraft$setBeingDeleted(true);

		//This might be in a world tick, if we don't schedule it, we might get a ConcurrentModificationException.
		TaskScheduler.scheduleImmediately(world.getServer(), () -> {
			boolean shouldRestore;
			if (server.getLevel(world.dimension()) != null) {
				shouldRestore = true;
				for (var player : new ArrayList<>(world.players())) {
					player.connection.disconnect(Component.literal("This dimension is being reset"));
				}
				handlePlayers.run();
				((MinecraftServerAccessor) server).getLevels().remove(world.dimension());
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
						((MinecraftServerAccessor) server).getStorageSource().getDimensionPath(world.dimension()),
						filesToNotRemove
				);
				if (world.getServer().isShutdown()) {
					return;
				}
				server.execute(() -> {
					if (shouldRestore) {
						var newWorld = new ServerLevel(
								server, ((MinecraftServerAccessor) server).getExecutor(),
								((MinecraftServerAccessor) server).getStorageSource(),
								new DerivedLevelData(
										server.getWorldData(), server.getWorldData().overworldData()
								),
								world.dimension(),
								server.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM)
										.getValue(world.dimension().identifier()),
								server.getWorldData().isDebugWorld(),
								BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()),
								ImmutableList.of(), false, server.overworld().getRandomSequences()
						);
						((MinecraftServerAccessor) server).getLevels().put(
								world.dimension(),
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

	public static void deleteWorldKillingPlayers(ServerLevel world, Runnable onCompleted, Predicate<Path> filesToNotRemove) {
		delete(world, onCompleted, filesToNotRemove, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world, player -> {
				player.putFloat("Health", 0);
				return true;
			});
		});
	}

	public static void deleteWorldTeleportingPlayers(
			ServerLevel world, Runnable onCompleted, Predicate<Path> filesToNotRemove,
			TeleportTransition targetPos
	) {
		deleteWorldTeleportingPlayers(world, onCompleted, filesToNotRemove, player -> targetPos);
	}

	public static void deleteWorldTeleportingPlayers(
			ServerLevel world, Runnable onCompleted, Predicate<Path> filesToNotRemove,
			Function<CompoundTag, TeleportTransition> targetPos
	) {
		delete(world, onCompleted, filesToNotRemove, () -> {
			DisconnectedPlayerHelper.forAllDisconnectedPlayers(world.getServer(), player -> {
				MutableBoolean modified = new MutableBoolean(false);
				if (DisconnectedPlayerHelper.getPlayerDim(player) == world.dimension()) {
					TeleportTransition target = targetPos.apply(player);
					DisconnectedPlayerHelper.setDim(player, target.newLevel().dimension());
					DisconnectedPlayerHelper.modifyPassengersAndRootVehicle(
							player, entity -> {
								DisconnectedPlayerHelper.setPos(entity, target.position());
								DisconnectedPlayerHelper.setVelocity(entity, target.position());
								DisconnectedPlayerHelper.setRotation(entity, target.yRot(), target.xRot());
							}
					);
					modified.setTrue();
				}
				if (DisconnectedPlayerHelper.getSpawnPoint(player).map(dim -> dim.respawnData().dimension()).orElse(null) == world.dimension()) {
					DisconnectedPlayerHelper.removeSpawnPoint(player);
					modified.setTrue();
				}
				if (player.contains(ServerPlayer.ENDER_PEARLS_TAG)) {
					var list = player.getListOrEmpty(ServerPlayer.ENDER_PEARLS_TAG);
					list.removeIf(nbt -> {
						if (nbt instanceof CompoundTag pearl) {
							if (DisconnectedPlayerHelper.getEnderPearlDim(pearl) == world.dimension()) {
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
