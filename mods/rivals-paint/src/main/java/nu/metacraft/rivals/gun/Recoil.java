package nu.metacraft.rivals.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A camera kick for vanilla clients: a relative pitch nudge up on the shot, eased back two ticks later.
 * Only real server players with a connection get packets; mock players are skipped.
 */
public final class Recoil {
	/** The ease-back is a fraction of whatever went up, so a heavy kick settles from higher. */
	public static final float SETTLE_FRACTION = 0.72f;
	private static final int SETTLE_DELAY_TICKS = 2;
	private static final List<Pending> SETTLE_QUEUE = new ArrayList<>();

	private record Pending(ServerPlayer player, long dueTick, float settlePitch) {}

	private Recoil() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SETTLE_QUEUE.isEmpty()) return;
			List<Pending> due = new ArrayList<>();
			for (Pending pending : List.copyOf(SETTLE_QUEUE)) {
				if (pending.dueTick() <= server.getTickCount()) {
					due.add(pending);
				}
			}
			for (Pending pending : due) {
				ServerPlayer player = pending.player();
				if (player.connection != null && !player.isRemoved()) {
					player.connection.send(new ClientboundPlayerRotationPacket(0f, true, pending.settlePitch(), true));
				}
			}
			SETTLE_QUEUE.removeAll(due);
		});
		// The queue is static and holds ServerPlayer references and absolute tick numbers, both of which
		// belong to the server that is going away: keeping them would leak the players and, since the
		// tick count restarts at 0, hold entries that never come due.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SETTLE_QUEUE.clear());
	}

	/** A kick of {@code pitch} degrees (negative is up), eased back two ticks later. */
	public static void kick(Player shooter, float pitch) {
		if (!(shooter instanceof ServerPlayer player) || player.connection == null) return;
		player.connection.send(new ClientboundPlayerRotationPacket(0f, true, pitch, true));
		long currentTick = player.level().getServer().getTickCount();
		SETTLE_QUEUE.add(new Pending(player, currentTick + SETTLE_DELAY_TICKS, -pitch * SETTLE_FRACTION));
	}

	public static int pending() {
		return SETTLE_QUEUE.size();
	}
}
