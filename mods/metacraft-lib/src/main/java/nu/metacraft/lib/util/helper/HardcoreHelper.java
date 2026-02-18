package nu.metacraft.lib.util.helper;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.mixin.*;

import java.util.ArrayList;
import java.util.List;

public class HardcoreHelper {

	/**
	 * Changes the given player's perceived hardcore mode state.
	 * @param player The player to send the state to.
	 * @param hardcore Whether hardcore mode should be enabled or disabled.
	 */
	public static void sendHardcoreState(ServerPlayer player, boolean hardcore) {
		Vec3 pos = player.position();
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		ServerLevel world = player.level();
		MinecraftServer server = player.level().getServer();
		PlayerList manager = server.getPlayerList();
		GameRules rules = world.getGameRules();

		//The only way to change the hardcore mode state is via the ClientJoinS2CPacket.
		//However, this breaks the world clientside if received while the world is loaded.
		//Hence, we must send all the packets normally necessary to send when respawning the player.
		player.connection.send(new ClientboundBundlePacket(
				List.of(
						new ClientboundLoginPacket(
								player.getId(), hardcore, server.levelKeys(),
								server.getMaxPlayers(), manager.getViewDistance(), manager.getSimulationDistance(),
								rules.get(GameRules.REDUCED_DEBUG_INFO), !rules.get(GameRules.IMMEDIATE_RESPAWN),
								rules.get(GameRules.LIMITED_CRAFTING),
								player.createCommonSpawnInfo(world), false
						),
						new ClientboundRespawnPacket(player.createCommonSpawnInfo(world), (byte) 3),
						new ClientboundSetDefaultSpawnPositionPacket(world.getRespawnData()),
						new ClientboundChangeDifficultyPacket(world.getDifficulty(), world.getLevelData().isDifficultyLocked()),
						new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel),
						new ClientboundPlayerAbilitiesPacket(player.getAbilities()),
						new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot())
				)
		));

		world.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
		((EntityAccessor) player).callUnsetRemoved();
		world.addRespawnedPlayer(player);
		player.level().getServer().getPlayerList().sendPlayerPermissionLevel(player);
		player.level().getServer().getPlayerList().sendLevelInfo(player, world);
		player.level().getServer().getPlayerList().sendAllPlayerInfo(player);
		player.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
		player.connection.resetPosition();

		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		packets.add(new ClientboundSetEntityMotionPacket(player));
		for (MobEffectInstance statusEffectInstance : player.getActiveEffects()) {
			packets.add(new ClientboundUpdateMobEffectPacket(player.getId(), statusEffectInstance, false));
		}
		player.connection.send(new ClientboundBundlePacket(packets));
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
		if (server.overworld().getLevelData() instanceof PrimaryLevelData properties) {
			LevelSettings info = properties.getLevelSettings();
			((PrimaryLevelDataAccessor) properties).setSettings(
					new LevelSettings(
							info.levelName(), info.gameType(),
							new LevelSettings.DifficultySettings(
									info.difficultySettings().difficulty(),
									hardcore,
									info.difficultySettings().locked()
							),
							info.allowCommands(), info.dataConfiguration()
					)
			);
			changed = true;
		}
		if (server instanceof DedicatedServerAccessor dedicated) {
			dedicated.getSettings().update(
					p -> {
						((DedicatedServerPropertiesAccessor) p).setHardcore(hardcore);
						((SettingsAccessor) p).getProperties().setProperty(
								"hardcore", Boolean.toString(hardcore)
						);
						return p;
					}
			);
			changed = true;
		}
		if (changed) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				sendHardcoreState(p, hardcore);
			}
		}
		return changed;
	}

}
