package se.datasektionen.mc.metacraft_dungeons.util;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.impl.event.interaction.FakePlayerNetworkHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.integrated.IntegratedPlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorIntegratedPlayerManager;
import se.datasektionen.mc.metacraft_dungeons.mixin.AccessorMinecraftServer;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class DisconnectedPlayerHelper {

	private static void loadPlayerData(
			ServerPlayerEntity player, MinecraftServer server, PlayerSaveHandler handler
	) {
		player.networkHandler = new FakePlayerNetworkHandler(player);
		var nbt = handler.loadPlayerData(player).orElse(null);
		player.setServerWorld(server.getWorld(
			nbt != null && nbt.contains("Dimension") ? World.CODEC.parse(
				NbtOps.INSTANCE, nbt.get("Dimension")
			).resultOrPartial(METAcraftDungeons.LOGGER::error).orElse(World.OVERWORLD) : World.OVERWORLD
		));
	}


	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param server The server to load and create players for.
	 * @param isPlayerOnline A predicate telling if the player is online or not.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	protected static void forAllDisconnectedPlayers(
			MinecraftServer server, Predicate<UUID> isPlayerOnline, Predicate<ServerPlayerEntity> playerAction
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

				ServerPlayerEntity player = server.getPlayerManager().createPlayer(
						Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(uuid)).orElse(
							new GameProfile(uuid, "Player")
						),
						SyncedClientOptions.createDefault()
				);
				loadPlayerData(player, server, handler);
				if (playerAction.test(player)) {
					handler.savePlayerData(player);
				}
			} catch (IllegalArgumentException e) {
				METAcraftDungeons.LOGGER.error(
						"Found invalid player data file " + playerId + " in player data folder."
				);
			}
		}
		NbtCompound singlePlayer = server.getSaveProperties().getPlayerData();
		if (singlePlayer != null && FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
			ServerPlayerEntity player = server.getPlayerManager().createPlayer(
					new GameProfile(UUID.randomUUID(), "Player"),
					SyncedClientOptions.createDefault()
			);
			loadPlayerData(player, server, handler);
			if (playerAction.test(player)) {
				updateUserData(server.getPlayerManager(), player);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static void updateUserData(PlayerManager manager, ServerPlayerEntity player) {
		if (manager instanceof IntegratedPlayerManager) {
			((AccessorIntegratedPlayerManager)manager).setUserData(player.writeNbt(new NbtCompound()));
		}
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param server The server to get players from.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(MinecraftServer server, Predicate<ServerPlayerEntity> playerAction) {
		forAllDisconnectedPlayers(server, uuid -> {
			for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
				if (onlinePlayer.getUuid().equals(uuid)) return true;
			}
			return false;
		}, playerAction);
	}

	/**
	 * Runs code for all players who are not connected to the server at that point.
	 * Use caution, certain things might crash the game, these are not real player entities in the world, just abstract ones for easier representation!
	 * @param world The world to get players from. Only players in this world will be affected.
	 * @param playerAction What to do with the player. It's a predicate to allow you to choose weather or not to save. Return true to save the player data, false to not save.
	 */
	public static void forAllDisconnectedPlayers(ServerWorld world, Predicate<ServerPlayerEntity> playerAction) {
		Predicate<ServerPlayerEntity> isInCorrectWorld = player -> world.equals(player.getWorld());
		forAllDisconnectedPlayers(world.getServer(), isInCorrectWorld.and(playerAction));
	}

}
