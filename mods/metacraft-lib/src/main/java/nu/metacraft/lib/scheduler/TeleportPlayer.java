package nu.metacraft.lib.scheduler;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.timer.Timer;
import net.minecraft.world.timer.TimerCallback;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.SerializableTeleportTarget;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;

import java.util.UUID;

public class TeleportPlayer implements TimerCallback<MinecraftServer>, Named {

	public static final MapCodec<TeleportPlayer> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Uuids.STRICT_CODEC.fieldOf("player").forGetter(t -> t.player),
					SerializableTeleportTarget.TELEPORT_TARGET_CODEC.fieldOf("target").forGetter(t -> t.target)
			).apply(instance, TeleportPlayer::new)
	);

	private final UUID player;
	private final SerializableTeleportTarget target;
	private TeleportTarget parsedTarget;

	public TeleportPlayer(
			UUID player,
			SerializableTeleportTarget target
	) {
		this.player = player;
		this.target = target;
	}

	public TeleportPlayer(PlayerEntity playerEntity, SerializableTeleportTarget target) {
		this(playerEntity.getUuid(), target);
	}

	@Override
	public String getName() {
		return METAcraftLib.getID(
				"player_teleport/" + this.player.toString()
		).toString();
	}

	@Override
	public void call(MinecraftServer server, Timer<MinecraftServer> events, long time) {
		if (parsedTarget == null) {
			var t = target.getIfFixed(server);
			if (t.isPresent()) {
				parsedTarget = t.get();
			} else {
				return;
			}
		}
		var player = server.getPlayerManager().getPlayer(this.player);
		if (player != null) {
			player.teleportTo(parsedTarget);
		} else {
			DisconnectedPlayerHelper.forDisconnectedPlayer(server, this.player, playerData -> {
				DisconnectedPlayerHelper.setFromTeleportTarget(playerData, parsedTarget);
				return true;
			});
		}
	}

	@Override
	public MapCodec<? extends TimerCallback<MinecraftServer>> getCodec() {
		return CODEC;
	}
}
