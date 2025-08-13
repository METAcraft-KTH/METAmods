package nu.metacraft.lib.util.helper;

import com.mojang.authlib.GameProfile;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DateTimeFormatters;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.mixin.AccessorMinecraftServer;
import nu.metacraft.lib.mixin.AccessorPlayerSaveHandler;
import nu.metacraft.lib.mixin.AccessorServerPlayerEntity;
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

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatters.create();

	private static GameProfile getProfile(UUID id, MinecraftServer server) {
		return Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(id)).orElse(
				new GameProfile(id, "missingno")
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
			Predicate<NbtCompound> playerAction
	) {
		var profile = getProfile(uuid, server);
		PlayerSaveHandler handler = ((AccessorMinecraftServer)server).getSaveHandler();
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
			MinecraftServer server, Predicate<UUID> isPlayerOnline, Predicate<NbtCompound> playerAction
	) {
		PlayerSaveHandler handler = ((AccessorMinecraftServer)server).getSaveHandler();
		var playerDir = ((AccessorMinecraftServer) server).getSession().getDirectory(WorldSavePath.PLAYERDATA).toFile();
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

	private static void savePlayerData(GameProfile player, NbtCompound nbt, PlayerSaveHandler handler) {
		try {
			NbtHelper.putDataVersion(nbt);
			var uuid = player.getId().toString();
			Path path = ((AccessorPlayerSaveHandler) handler).getPlayerDataDir().toPath();
			Path tmp = Files.createTempFile(path, uuid + "-", ".dat");
			NbtIo.writeCompressed(nbt, tmp);
			Path data = path.resolve(uuid + ".dat");
			Path old = path.resolve(uuid + ".dat_old");
			Util.backupAndReplace(data, tmp, old);
		} catch (Exception var7) {
			METAcraftLib.LOGGER.warn("Failed to save player data for {}", player.getName());
		}
	}

	private static Optional<NbtCompound> loadPlayerData(GameProfile player, String extension, PlayerSaveHandler handler) {
		File path = ((AccessorPlayerSaveHandler) handler).getPlayerDataDir();
		String uuid = player.getId().toString();
		File file = new File(path, uuid + extension);
		if (file.exists() && file.isFile()) {
			try {
				return Optional.of(NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes()));
			} catch (Exception var5) {
				METAcraftLib.LOGGER.warn("Failed to load player data for {}", player.getName());
			}
		}

		return Optional.empty();
	}

	private static void backupCorruptedPlayerData(GameProfile player, String extension, PlayerSaveHandler handler) {
		Path path = ((AccessorPlayerSaveHandler) handler).getPlayerDataDir().toPath();
		String uuid = player.getId().toString();
		Path data = path.resolve(uuid + extension);
		Path corrupted = path.resolve(uuid + "_corrupted_" + LocalDateTime.now().format(DATE_TIME_FORMATTER) + extension);
		if (Files.isRegularFile(data)) {
			try {
				Files.copy(data, corrupted, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (Exception exception) {
				METAcraftLib.LOGGER.warn("Failed to copy the player.dat file for {}", player.getName(), exception);
			}

		}
	}

	private static Optional<NbtCompound> loadPlayerData(GameProfile player, PlayerSaveHandler handler) {
		Optional<NbtCompound> optional = loadPlayerData(player, ".dat", handler);
		if (optional.isEmpty()) {
			backupCorruptedPlayerData(player, ".dat", handler);
		}

		return optional.or(() -> loadPlayerData(player, ".dat_old", handler)).map((nbt) -> {
			int i = NbtHelper.getDataVersion(nbt, -1);
			nbt = DataFixTypes.PLAYER.update(((AccessorPlayerSaveHandler) handler).getDataFixer(), nbt, i);
			return nbt;
		});
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param server The server to get players from.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(MinecraftServer server, Predicate<NbtCompound> playerAction) {
		forAllDisconnectedPlayers(server, uuid -> {
			for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
				if (onlinePlayer.getUuid().equals(uuid)) return true;
			}
			return false;
		}, playerAction);
	}

	public static RegistryKey<World> getPlayerDim(NbtCompound nbt) {
		return nbt.get("Dimension", World.CODEC).orElse(World.OVERWORLD);
	}

	public static RegistryKey<World> getEnderPearlDim(NbtCompound nbt) {
		return nbt.get(ServerPlayerEntity.ENDER_PEARLS_DIMENSION_KEY, World.CODEC).orElse(World.OVERWORLD);
	}

	public static void setDim(NbtCompound nbt, RegistryKey<World> dim) {
		nbt.putString("Dimension", dim.getValue().toString());
	}

	public static void modifyPassengersAndRootVehicle(NbtCompound player, Consumer<NbtCompound> nbtModifier) {
		nbtModifier.accept(player);
		if (player.contains("RootVehicle")) {
			var entity = player.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity");
			modifyPassengersAndRootVehicle(entity, nbtModifier);
		}
		if (player.contains(PlayerEntity.PASSENGERS_KEY)) {
			var list = player.getListOrEmpty(PlayerEntity.PASSENGERS_KEY);
			for (var e : list) {
				if (e instanceof NbtCompound passenger) {
					modifyPassengersAndRootVehicle(passenger, nbtModifier);
				}
			}
		}
	}

	public static void deleteVehicleAndPassengers(NbtCompound player) {
		player.remove("RootVehicle");
		player.remove(PlayerEntity.PASSENGERS_KEY);
	}

	public static void setHealth(NbtCompound player, float health) {
		player.putFloat("Health", health);
	}

	public static void kill(NbtCompound player) {
		setHealth(player, 0);
	}

	public static void setPos(NbtCompound nbt, Vec3d pos) {
		nbt.put("Pos", Vec3d.CODEC, pos);
	}

	public static void setVelocity(NbtCompound nbt, Vec3d velocity) {
		nbt.put("Motion", Vec3d.CODEC, velocity);
	}

	public static PlayerPosition getPlayerPosition(NbtCompound nbt) {
		return new PlayerPosition(
				getPos(nbt),
				getVelocity(nbt),
				getYaw(nbt),
				getPitch(nbt)
		);
	}

	public static Vec3d getPos(NbtCompound nbt) {
		return nbt.get("Pos", Vec3d.CODEC).orElse(Vec3d.ZERO);
	}

	public static Vec3d getVelocity(NbtCompound nbt) {
		return nbt.get("Motion", Vec3d.CODEC).orElse(Vec3d.ZERO);
	}

	public static float getYaw(NbtCompound nbt) {
		return nbt.get("Rotation", Vec2f.CODEC).map(f -> f.x).orElse(0.0f);
	}

	public static float getPitch(NbtCompound nbt) {
		return nbt.get("Rotation", Vec2f.CODEC).map(f -> f.y).orElse(0.0f);
	}

	public static void setRotation(NbtCompound nbt, float yaw, float pitch) {
		nbt.put("Rotation", Vec2f.CODEC, new Vec2f(yaw, pitch));
	}

	public static void setPlayerPosition(NbtCompound player, PlayerPosition position) {
		modifyPassengersAndRootVehicle(
				player, entity -> {
					setPos(entity, position.position());
					setVelocity(entity, position.deltaMovement());
					setRotation(entity, position.yaw(), position.pitch());
				}
		);
	}

	public static void setFromTeleportTarget(NbtCompound player, TeleportTarget target) {
		setDim(player, target.world().getRegistryKey());
		PlayerPosition actualTarget = PlayerPosition.apply(
				getPlayerPosition(player),
				PlayerPosition.fromTeleportTarget(target),
				target.relatives()
		);
		setPlayerPosition(player, actualTarget);
	}

	public static Optional<ServerPlayerEntity.Respawn> getSpawnPoint(NbtCompound nbt) {
		return nbt.get("respawn", ServerPlayerEntity.Respawn.CODEC);
	}

	public static TeleportTarget getRespawnTarget(
			@Nullable ServerPlayerEntity.Respawn respawn, MinecraftServer server,
			boolean drainRespawnAnchor, TeleportTarget.PostDimensionTransition postDimensionTransition
	) {
		//Basically just Mojang's function in ServerPlayerEntity, but now it's static.
		ServerWorld serverWorld = server.getWorld(respawn != null ? respawn.dimension() : World.OVERWORLD);
		if (serverWorld != null && respawn != null) {
			Optional<ServerPlayerEntity.RespawnPos> optional = AccessorServerPlayerEntity.callFindRespawnPosition(serverWorld, respawn, drainRespawnAnchor);
			if (optional.isPresent()) {
				ServerPlayerEntity.RespawnPos respawnPos = optional.get();
				return new TeleportTarget(serverWorld, respawnPos.pos(), Vec3d.ZERO, respawnPos.yaw(), 0.0F, postDimensionTransition);
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

	public static void removeSpawnPoint(NbtCompound nbt) {
		nbt.remove("respawn");
	}

	public static boolean removeSpawnPointIfMatching(NbtCompound nbt, BiPredicate<RegistryKey<World>, BlockPos> dim) {
		var spawnPoint = DisconnectedPlayerHelper.getSpawnPoint(nbt);
		if (spawnPoint.isPresent() && dim.test(spawnPoint.get().dimension(), spawnPoint.get().pos())) {
			DisconnectedPlayerHelper.removeSpawnPoint(nbt);
			return true;
		}
		return false;
	}

	public static boolean removeEnderPearlsIfMatching(NbtCompound nbt, BiPredicate<RegistryKey<World>, Vec3d> dim) {
		MutableBoolean modified = new MutableBoolean(false);
		if (nbt.contains(ServerPlayerEntity.ENDER_PEARLS_KEY)) {
			var list = nbt.getListOrEmpty(ServerPlayerEntity.ENDER_PEARLS_KEY);
			list.removeIf(p -> {
				if (p instanceof NbtCompound pearl) {
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
	public static void forAllDisconnectedPlayers(ServerWorld world, Predicate<NbtCompound> playerAction) {
		Predicate<NbtCompound> isInCorrectWorld = player -> world.getRegistryKey() == getPlayerDim(player);
		forAllDisconnectedPlayers(world.getServer(), isInCorrectWorld.and(playerAction));
	}

}
