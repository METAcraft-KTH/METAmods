package nu.metacraft.lib.scheduler;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerQueue;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.SerializableTeleportTarget;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;

import java.util.UUID;

public class TeleportPlayer implements TimerCallback<MinecraftServer>, Named {

	public static final MapCodec<TeleportPlayer> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					UUIDUtil.LENIENT_CODEC.fieldOf("player").forGetter(t -> t.player),
					SerializableTeleportTarget.TELEPORT_TARGET_CODEC.fieldOf("target").forGetter(t -> t.target)
			).apply(instance, TeleportPlayer::new)
	);

	private final UUID player;
	private final SerializableTeleportTarget target;
	private TeleportTransition parsedTarget;

	public TeleportPlayer(
			UUID player,
			SerializableTeleportTarget target
	) {
		this.player = player;
		this.target = target;
	}

	public TeleportPlayer(Player playerEntity, SerializableTeleportTarget target) {
		this(playerEntity.getUUID(), target);
	}

	@Override
	public String getName() {
		return METAcraftLib.getID(
				"player_teleport/" + this.player.toString()
		).toString();
	}

	@Override
	public void handle(MinecraftServer server, TimerQueue<MinecraftServer> events, long time) {
		if (parsedTarget == null) {
			var t = target.getIfFixed(server);
			if (t.isPresent()) {
				parsedTarget = t.get();
			} else {
				return;
			}
		}
		var player = server.getPlayerList().getPlayer(this.player);
		if (player != null) {
			player.teleport(parsedTarget);
		} else {
			DisconnectedPlayerHelper.forDisconnectedPlayer(server, this.player, playerData -> {
				DisconnectedPlayerHelper.setFromTeleportTarget(playerData, parsedTarget);
				return true;
			});
		}
	}

	@Override
	public MapCodec<? extends TimerCallback<MinecraftServer>> codec() {
		return CODEC;
	}
}
