package nu.metacraft.lib.util.helper;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.FileNameDateFormatter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.mixin.MinecraftServerAccessor;
import nu.metacraft.lib.mixin.PlayerDataStorageAccessor;
import nu.metacraft.lib.mixin.ServerPlayerAccessor;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DisconnectedPlayerHelper {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = FileNameDateFormatter.create();

	private static NameAndId getProfile(UUID id, MinecraftServer server) {
		return Optional.ofNullable(server.services().nameToIdCache()).flatMap(cache -> cache.get(id)).orElse(
				new NameAndId(id, "missingno")
		);
	}

	/**
	 * Allows direct modification of the player data for a specific player.
	 * Can be used to modify players while they are offline.
	 * @param server The server to run on.
	 * @param uuid The UUID of the player to modify.
	 * @param playerAction The modification to run. Return true to save the player data, false to skip saving (and thus not change anything).
	 */
	public static void forDisconnectedPlayer(
			MinecraftServer server, UUID uuid,
			Predicate<CompoundTag> playerAction
	) {
		var profile = getProfile(uuid, server);
		PlayerDataStorage handler = ((MinecraftServerAccessor)server).getPlayerDataStorage();
		var nbt = loadPlayerData(profile, handler).orElse(null);
		if (nbt != null) {
			if (playerAction.test(nbt)) {
				savePlayerData(profile, nbt, handler);
			}
		}
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * @param server The server to load and create players for.
	 * @param isPlayerOnline A predicate telling if the player is online or not.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	protected static void forAllDisconnectedPlayers(
			MinecraftServer server, Predicate<UUID> isPlayerOnline, Predicate<CompoundTag> playerAction
	) {
		var playerDir = ((MinecraftServerAccessor) server).getStorageSource().getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
		String[] ids = Optional.ofNullable(playerDir.list()).map(
				playerFiles -> Arrays.stream(playerFiles).filter(id -> id.endsWith(".dat")).map(
						id -> id.substring(0, id.length()-4)
				).toArray(String[]::new)
		).orElse(new String[0]);
		for (String playerId : ids) {
			try {
				UUID uuid = UUID.fromString(playerId);

				if (isPlayerOnline.test(uuid)) continue;

				forDisconnectedPlayer(server, uuid, playerAction);
			} catch (IllegalArgumentException e) {
				METAcraftLib.LOGGER.error("Found invalid player data file {} in player data folder.", playerId);
			}
		}
	}

	private static void savePlayerData(NameAndId player, CompoundTag nbt, PlayerDataStorage handler) {
		try {
			NbtUtils.addCurrentDataVersion(nbt);
			var uuid = player.id().toString();
			Path path = ((PlayerDataStorageAccessor) handler).getPlayerDir().toPath();
			Path tmp = Files.createTempFile(path, uuid + "-", ".dat");
			NbtIo.writeCompressed(nbt, tmp);
			Path data = path.resolve(uuid + ".dat");
			Path old = path.resolve(uuid + ".dat_old");
			Util.safeReplaceFile(data, tmp, old);
		} catch (Exception var7) {
			METAcraftLib.LOGGER.warn("Failed to save player data for {}", player.name());
		}
	}

	private static Optional<CompoundTag> loadPlayerData(NameAndId player, String extension, PlayerDataStorage handler) {
		File path = ((PlayerDataStorageAccessor) handler).getPlayerDir();
		String uuid = player.id().toString();
		File file = new File(path, uuid + extension);
		if (file.exists() && file.isFile()) {
			try {
				return Optional.of(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
			} catch (Exception var5) {
				METAcraftLib.LOGGER.warn("Failed to load player data for {}", player.name());
			}
		}

		return Optional.empty();
	}

	private static void backupCorruptedPlayerData(NameAndId player, String extension, PlayerDataStorage handler) {
		Path path = ((PlayerDataStorageAccessor) handler).getPlayerDir().toPath();
		String uuid = player.id().toString();
		Path data = path.resolve(uuid + extension);
		Path corrupted = path.resolve(uuid + "_corrupted_" + LocalDateTime.now().format(DATE_TIME_FORMATTER) + extension);
		if (Files.isRegularFile(data)) {
			try {
				Files.copy(data, corrupted, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (Exception exception) {
				METAcraftLib.LOGGER.warn("Failed to copy the player.dat file for {}", player.name(), exception);
			}

		}
	}

	private static Optional<CompoundTag> loadPlayerData(NameAndId player, PlayerDataStorage handler) {
		Optional<CompoundTag> optional = loadPlayerData(player, ".dat", handler);
		if (optional.isEmpty()) {
			backupCorruptedPlayerData(player, ".dat", handler);
		}

		return optional.or(() -> loadPlayerData(player, ".dat_old", handler)).map((nbt) -> {
			int i = NbtUtils.getDataVersion(nbt, -1);
			nbt = DataFixTypes.PLAYER.updateToCurrentVersion(((PlayerDataStorageAccessor) handler).getFixerUpper(), nbt, i);
			return nbt;
		});
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param server The server to get players from.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(MinecraftServer server, Predicate<CompoundTag> playerAction) {
		forAllDisconnectedPlayers(server, uuid -> {
			for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
				if (onlinePlayer.getUUID().equals(uuid)) return true;
			}
			return false;
		}, playerAction);
	}

	public static ResourceKey<Level> getPlayerDim(CompoundTag nbt) {
		return nbt.read("Dimension", Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
	}

	public static ResourceKey<Level> getEnderPearlDim(CompoundTag nbt) {
		return nbt.read(ServerPlayer.ENDER_PEARL_DIMENSION_TAG, Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
	}

	public static void setDim(CompoundTag nbt, ResourceKey<Level> dim) {
		nbt.putString("Dimension", dim.location().toString());
	}

	public static void modifyPassengersAndRootVehicle(CompoundTag player, Consumer<CompoundTag> nbtModifier) {
		nbtModifier.accept(player);
		if (player.contains("RootVehicle")) {
			var entity = player.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity");
			modifyPassengersAndRootVehicle(entity, nbtModifier);
		}
		if (player.contains(Player.TAG_PASSENGERS)) {
			var list = player.getListOrEmpty(Player.TAG_PASSENGERS);
			for (var e : list) {
				if (e instanceof CompoundTag passenger) {
					modifyPassengersAndRootVehicle(passenger, nbtModifier);
				}
			}
		}
	}

	public static void deleteVehicleAndPassengers(CompoundTag player) {
		player.remove("RootVehicle");
		player.remove(Player.TAG_PASSENGERS);
	}

	public static void setHealth(CompoundTag player, float health) {
		player.putFloat("Health", health);
	}

	public static void kill(CompoundTag player) {
		setHealth(player, 0);
	}

	public static void setPos(CompoundTag nbt, Vec3 pos) {
		nbt.store("Pos", Vec3.CODEC, pos);
	}

	public static void setVelocity(CompoundTag nbt, Vec3 velocity) {
		nbt.store("Motion", Vec3.CODEC, velocity);
	}

	public static PositionMoveRotation getPlayerPosition(CompoundTag nbt) {
		return new PositionMoveRotation(
				getPos(nbt),
				getVelocity(nbt),
				getYaw(nbt),
				getPitch(nbt)
		);
	}

	public static Vec3 getPos(CompoundTag nbt) {
		return nbt.read("Pos", Vec3.CODEC).orElse(Vec3.ZERO);
	}

	public static Vec3 getVelocity(CompoundTag nbt) {
		return nbt.read("Motion", Vec3.CODEC).orElse(Vec3.ZERO);
	}

	public static float getYaw(CompoundTag nbt) {
		return nbt.read("Rotation", Vec2.CODEC).map(f -> f.x).orElse(0.0f);
	}

	public static float getPitch(CompoundTag nbt) {
		return nbt.read("Rotation", Vec2.CODEC).map(f -> f.y).orElse(0.0f);
	}

	public static void setRotation(CompoundTag nbt, float yaw, float pitch) {
		nbt.store("Rotation", Vec2.CODEC, new Vec2(yaw, pitch));
	}

	public static void setPlayerPosition(CompoundTag player, PositionMoveRotation position) {
		modifyPassengersAndRootVehicle(
				player, entity -> {
					setPos(entity, position.position());
					setVelocity(entity, position.deltaMovement());
					setRotation(entity, position.yRot(), position.xRot());
				}
		);
	}

	public static void setFromTeleportTarget(CompoundTag player, TeleportTransition target) {
		setDim(player, target.newLevel().dimension());
		PositionMoveRotation actualTarget = PositionMoveRotation.calculateAbsolute(
				getPlayerPosition(player),
				PositionMoveRotation.of(target),
				target.relatives()
		);
		setPlayerPosition(player, actualTarget);
	}

	public static Optional<ServerPlayer.RespawnConfig> getSpawnPoint(CompoundTag nbt) {
		return nbt.read("respawn", ServerPlayer.RespawnConfig.CODEC);
	}

	public static TeleportTransition getRespawnTarget(
			@Nullable ServerPlayer.RespawnConfig respawn, MinecraftServer server,
			boolean drainRespawnAnchor, TeleportTransition.PostTeleportTransition postDimensionTransition
	) {
		//Basically just Mojang's function in ServerPlayerEntity, but now it's static.
		ServerLevel serverWorld = server.getLevel(respawn != null ? respawn.respawnData().dimension() : Level.OVERWORLD);
		if (serverWorld != null && respawn != null) {
			Optional<ServerPlayer.RespawnPosAngle> optional = ServerPlayerAccessor.callFindRespawnAndUseSpawnBlock(serverWorld, respawn, drainRespawnAnchor);
			if (optional.isPresent()) {
				ServerPlayer.RespawnPosAngle respawnPos = optional.get();
				return new TeleportTransition(serverWorld, respawnPos.position(), Vec3.ZERO, respawnPos.yaw(), 0.0F, postDimensionTransition);
			} else {
				return TeleportHelper.getOverworldSpawn(
						server, true, postDimensionTransition
				);
			}
		} else {
			return TeleportHelper.getOverworldSpawn(
					server, false, postDimensionTransition
			);
		}
	}

	public static void removeSpawnPoint(CompoundTag nbt) {
		nbt.remove("respawn");
	}

	public static boolean removeSpawnPointIfMatching(CompoundTag nbt, BiPredicate<ResourceKey<Level>, BlockPos> dim) {
		var spawnPoint = DisconnectedPlayerHelper.getSpawnPoint(nbt);
		if (spawnPoint.isPresent() && dim.test(spawnPoint.get().respawnData().dimension(), spawnPoint.get().respawnData().pos())) {
			DisconnectedPlayerHelper.removeSpawnPoint(nbt);
			return true;
		}
		return false;
	}

	public static boolean removeEnderPearlsIfMatching(CompoundTag nbt, BiPredicate<ResourceKey<Level>, Vec3> dim) {
		MutableBoolean modified = new MutableBoolean(false);
		if (nbt.contains(ServerPlayer.ENDER_PEARLS_TAG)) {
			var list = nbt.getListOrEmpty(ServerPlayer.ENDER_PEARLS_TAG);
			list.removeIf(p -> {
				if (p instanceof CompoundTag pearl) {
					if (dim.test(getEnderPearlDim(pearl), getPos(pearl))) {
						modified.setTrue();
						return true;
					}
				}
				return false;
			});
		}
		return modified.booleanValue();
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * @param world The world to get players from. Only players in this world will be affected.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(ServerLevel world, Predicate<CompoundTag> playerAction) {
		Predicate<CompoundTag> isInCorrectWorld = player -> world.dimension() == getPlayerDim(player);
		forAllDisconnectedPlayers(world.getServer(), isInCorrectWorld.and(playerAction));
	}

}
