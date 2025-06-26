package se.datasektionen.mc.metacraft_dungeons.util;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.integrated.IntegratedPlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DateTimeFormatters;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorIntegratedPlayerManager;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorMinecraftServer;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorPlayerSaveHandler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
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
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
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

				var profile = getProfile(uuid, server);
				var nbt = loadPlayerData(profile, handler).orElse(null);
				if (nbt != null) {
					if (playerAction.test(nbt)) {
						savePlayerData(profile, nbt, handler);
					}
				}
			} catch (IllegalArgumentException e) {
				METAcraftDungeons.LOGGER.error(
						"Found invalid player data file " + playerId + " in player data folder."
				);
			}
		}
		NbtCompound singlePlayer = server.getSaveProperties().getPlayerData();
		if (singlePlayer != null && FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
			if (playerAction.test(singlePlayer)) {
				updateUserData(server.getPlayerManager(), singlePlayer);
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
			METAcraftDungeons.LOGGER.warn("Failed to save player data for {}", player.getName());
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
				METAcraftDungeons.LOGGER.warn("Failed to load player data for {}", player.getName());
			}
		}

		return Optional.empty();
	}

	private static void backupCorruptedPlayerData(GameProfile player, String extension, PlayerSaveHandler handler) {
		Path path = ((AccessorPlayerSaveHandler) handler).getPlayerDataDir().toPath();
		String uuid = player.getId().toString();
		Path data = path.resolve(uuid + extension);
		Path corrupted = path.resolve(uuid + "_corrupted_" + LocalDateTime.now().format(DATE_TIME_FORMATTER) + extension);
		if (Files.isRegularFile(data, new LinkOption[0])) {
			try {
				Files.copy(data, corrupted, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (Exception exception) {
				METAcraftDungeons.LOGGER.warn("Failed to copy the player.dat file for {}", player.getName(), exception);
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

	@Environment(EnvType.CLIENT)
	public static void updateUserData(PlayerManager manager, NbtCompound nbt) {
		if (manager instanceof IntegratedPlayerManager) {
			NbtHelper.putDataVersion(nbt);
			((AccessorIntegratedPlayerManager)manager).setUserData(nbt);
		}
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

	public static void setPos(NbtCompound nbt, Vec3d pos) {
		nbt.put("Pos", Vec3d.CODEC, pos);
	}

	public static void setVelocity(NbtCompound nbt, Vec3d velocity) {
		nbt.put("Motion", Vec3d.CODEC, velocity);
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

	public static RegistryKey<World> getSpawnPointDimension(NbtCompound nbt) {
		return nbt.get("respawn", ServerPlayerEntity.Respawn.CODEC).map(ServerPlayerEntity.Respawn::dimension).orElse(World.OVERWORLD);
	}

	public static void removeSpawnPoint(NbtCompound nbt) {
		nbt.remove("respawn");
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param world The world to get players from. Only players in this world will be affected.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(ServerWorld world, Predicate<NbtCompound> playerAction) {
		Predicate<NbtCompound> isInCorrectWorld = player -> world.getRegistryKey() == getPlayerDim(player);
		forAllDisconnectedPlayers(world.getServer(), isInCorrectWorld.and(playerAction));
	}

}
