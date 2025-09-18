package nu.metacraft.lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import nu.metacraft.lib.mixin.*;

import java.util.ArrayList;
import java.util.List;

public class HardcoreHelper {

	/**
	 * Changes the given player's perceived hardcore mode state.
	 * @param player The player to send the state to.
	 * @param hardcore Whether hardcore mode should be enabled or disabled.
	 */
	public static void sendHardcoreState(ServerPlayerEntity player, boolean hardcore) {
		Vec3d pos = player.getPos();
		float yaw = player.getYaw();
		float pitch = player.getPitch();
		ServerWorld world = player.getEntityWorld();
		MinecraftServer server = player.getEntityWorld().getServer();
		PlayerManager manager = server.getPlayerManager();
		GameRules rules = world.getGameRules();

		//The only way to change the hardcore mode state is via the ClientJoinS2CPacket.
		//However, this breaks the world clientside if received while the world is loaded.
		//Hence, we must send all the packets normally necessary to send when respawning the player.
		player.networkHandler.sendPacket(new BundleS2CPacket(
				List.of(
						new GameJoinS2CPacket(
								player.getId(), hardcore, server.getWorldRegistryKeys(),
								server.getMaxPlayerCount(), manager.getViewDistance(), manager.getSimulationDistance(),
								rules.getBoolean(GameRules.REDUCED_DEBUG_INFO), !rules.getBoolean(GameRules.DO_IMMEDIATE_RESPAWN),
								rules.getBoolean(GameRules.DO_LIMITED_CRAFTING),
								player.createCommonPlayerSpawnInfo(world), false
						),
						new PlayerRespawnS2CPacket(player.createCommonPlayerSpawnInfo(world), (byte) 3),
						new PlayerSpawnPositionS2CPacket(world.method_74854()),
						new DifficultyS2CPacket(world.getDifficulty(), world.getLevelProperties().isDifficultyLocked()),
						new ExperienceBarUpdateS2CPacket(player.experienceProgress, player.totalExperience, player.experienceLevel),
						new PlayerAbilitiesS2CPacket(player.getAbilities()),
						new UpdateSelectedSlotS2CPacket(player.getInventory().getSelectedSlot())
				)
		));

		world.removePlayer(player, Entity.RemovalReason.CHANGED_DIMENSION);
		((AccessorEntity) player).callUnsetRemoved();
		world.onPlayerRespawned(player);
		player.getEntityWorld().getServer().getPlayerManager().sendCommandTree(player);
		player.getEntityWorld().getServer().getPlayerManager().sendWorldInfo(player, world);
		player.getEntityWorld().getServer().getPlayerManager().sendPlayerStatus(player);
		player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, pitch);
		player.networkHandler.syncWithPlayerPosition();

		List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
		packets.add(new EntityVelocityUpdateS2CPacket(player));
		for (StatusEffectInstance statusEffectInstance : player.getStatusEffects()) {
			packets.add(new EntityStatusEffectS2CPacket(player.getId(), statusEffectInstance, false));
		}
		player.networkHandler.sendPacket(new BundleS2CPacket(packets));
	}

	/**
	 * Toggles hardcore mode for the server. The change will be visible to the players immediately.
	 * @param server The server to change the hardcore mode state of.
	 * @param hardcore Whether hardcore should be enabled or disabled.
	 * @return True if the state was changed, false otherwise.
	 */
	public static boolean setHardcoreMode(MinecraftServer server, boolean hardcore) {
		if (server.isHardcore() == hardcore) return false;
		boolean changed = false;
		if (server.getOverworld().getLevelProperties() instanceof LevelProperties properties) {
			LevelInfo info = properties.getLevelInfo();
			((AccessorLevelProperties) properties).setLevelInfo(
					new LevelInfo(
							info.getLevelName(), info.getGameMode(), hardcore,
							info.getDifficulty(), info.areCommandsAllowed(),
							info.getGameRules(), info.getDataConfiguration()
					)
			);
			changed = true;
		}
		if (server instanceof AccessorMinecraftDedicatedServer dedicated) {
			dedicated.getPropertiesLoader().apply(
					p -> {
						((AccessorServerPropertiesHandler) p).setHardcore(hardcore);
						((AccessorAbstractPropertiesHandler) p).getProperties().setProperty(
								"hardcore", Boolean.toString(hardcore)
						);
						return p;
					}
			);
			changed = true;
		}
		if (changed) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				sendHardcoreState(p, hardcore);
			}
		}
		return changed;
	}

}
