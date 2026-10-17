package nu.metacraft.qol.void_anchor.rift;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.qol.void_anchor.AnchorBinding;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catches players falling into the End void. Below the End's lowest Y there is nothing to stand
 * on, so a bound player there is stopped in mid-air and their view tilted down while a rift cracks
 * open beneath them, then sinks into it slowly and is taken to their anchor. The anchor is checked again at the last moment, and a charge is spent only when
 * the player is actually moved.
 */
public final class RiftTracker {

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Players whose current fall has been dealt with; forgotten once they are back above the trigger or out of the End. */
	private static final Set<UUID> HANDLED = new HashSet<>();
	/** How fast a caught player drifts down while the rift opens under them, in blocks per tick. */
	private static final double HANG_DRIFT = 0.02;
	/** Over the first ticks of the hang the player's view tilts down to this pitch, so they see the rift open. */
	private static final float LOOK_PITCH = 80f;
	private static final int LOOK_TICKS = 8;
	/**
	 * A caught player's client keeps falling until the hang reaches it, a tick or more at full
	 * speed. The rift waits until they have stopped (moved less than this in a tick, or at most
	 * MAX_STOPPING ticks), so it opens below where they hang, not where they were caught.
	 */
	private static final double SETTLED = 0.5;
	/** The screen whites out this many ticks before the rift takes them; they arrive while it is white. */
	private static final int WHITE_IN = 4;
	private static final int WHITE_HOLD = 4;
	private static final int WHITE_OUT = 16;
	/** The rift a rescued player steps out of stays open this long. */
	private static final int EXIT_TICKS = 50;
	private static final int MAX_STOPPING = 6;

	private static final class Session {
		final float startPitch;
		double lastY;
		int ticks;
		/** Null while the player is still stopping. */
		@Nullable Rift rift;
		Vec3 centre;
		int openedAt;
		/** Opened as the screen whites out; null until then. */
		@Nullable Rift exit;

		Session(ServerPlayer player) {
			this.startPitch = player.getXRot();
			this.lastY = player.getY();
			this.centre = player.position();
		}

		void close() {
			if (rift != null) {
				rift.close();
			}
			if (exit != null) {
				exit.close();
			}
		}
	}

	private RiftTracker() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RiftTracker::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			HANDLED.clear();
		});
	}

	public static boolean isRifting(UUID player) {
		return SESSIONS.containsKey(player);
	}

	private static void tick(MinecraftServer server) {
		var end = server.getLevel(Level.END);
		var config = VoidAnchorConfig.getInstance();
		if (end == null) {
			advance(server, null, Double.NEGATIVE_INFINITY, config);
			return;
		}
		double trigger = end.getMinY() + config.triggerYOffset();
		advance(server, end, trigger, config);
		HANDLED.removeIf(id -> {
			var player = server.getPlayerList().getPlayer(id);
			return player == null || player.level() != end || player.getY() >= trigger;
		});
		for (var player : end.players()) {
			var id = player.getUUID();
			if (!config.enabled() || player.getY() >= trigger || SESSIONS.containsKey(id) || HANDLED.contains(id)) {
				continue;
			}
			// Creative players can fly out and shouldn't drain an anchor others may share.
			if (player.isSpectator() || player.isCreative() || player.getAbilities().flying || !player.isAlive() || player.isPassenger()) {
				continue;
			}
			start(player, end, config);
		}
	}

	private static void start(ServerPlayer player, ServerLevel end, VoidAnchorConfig config) {
		var resolution = AnchorBinding.resolve(player);
		if (resolution instanceof AnchorBinding.Ready) {
			SESSIONS.put(player.getUUID(), new Session(player));
			ease(player, player.position(), HANG_DRIFT);
		} else {
			HANDLED.add(player.getUUID());
			hint(player, resolution);
		}
	}

	private static void advance(MinecraftServer server, @Nullable ServerLevel end, double trigger, VoidAnchorConfig config) {
		var it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			var session = entry.getValue();
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != end || player.getY() >= trigger) {
				// Gone, dead, elsewhere, or saved themselves (a pearl back up): close up and spend nothing.
				session.close();
				it.remove();
				continue;
			}
			session.ticks++;
			if (session.rift == null) {
				boolean stopped = Math.abs(player.getY() - session.lastY) < SETTLED || session.ticks >= MAX_STOPPING;
				session.lastY = player.getY();
				if (stopped) {
					session.centre = new Vec3(player.getX(), player.getY() - config.riftDepth(), player.getZ());
					session.rift = Rift.open(end, session.centre, config.riftSize(), config.riftStyle());
					session.openedAt = session.ticks;
				} else {
					session.centre = player.position();
				}
				ease(player, session.centre, HANG_DRIFT);
				look(player, session);
				continue;
			}
			int since = session.ticks - session.openedAt;
			int opening = session.rift.openTicks();
			boolean open = since > opening;
			if (session.exit == null && open && (since >= opening + config.riftTicks() - WHITE_IN
					|| player.getY() <= session.centre.y + 0.5 + config.descentSpeed() * WHITE_IN)) {
				// a few ticks out: white the screen out and open the way out at the anchor
				if (AnchorBinding.resolve(player) instanceof AnchorBinding.Ready ready) {
					WhiteOut.send(player, WHITE_IN, WHITE_HOLD, WHITE_OUT);
					session.exit = Rift.openExit(ready.level(), ready.anchor(), ready.standUp());
				}
			}
			if (since >= opening + config.riftTicks() || (open && player.getY() <= session.centre.y + 0.5)) {
				it.remove();
				HANDLED.add(player.getUUID());
				session.rift.close();
				if (finish(player)) {
					if (session.exit != null) {
						session.exit.closeAfter(EXIT_TICKS);
					}
				} else if (session.exit != null) {
					session.exit.close();
				}
			} else {
				ease(player, session.centre, open ? config.descentSpeed() : HANG_DRIFT);
				look(player, session);
			}
		}
	}

	/**
	 * Holds the fall to a slow drift toward the rift's centre; syncVelocity sends the motion to the
	 * client. A glider stops gliding, as on hitting water, or the elytra would fight the hang.
	 */
	private static void ease(ServerPlayer player, Vec3 centre, double sink) {
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
		double dx = Mth.clamp((centre.x - player.getX()) * 0.25, -0.5, 0.5);
		double dz = Mth.clamp((centre.z - player.getZ()) * 0.25, -0.5, 0.5);
		player.setDeltaMovement(dx, -sink, dz);
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	/**
	 * Tilts the player's view down toward the rift, eased over LOOK_TICKS. Each step is sent
	 * relative to wherever they are looking, so turning the mouse meanwhile still works.
	 */
	private static void look(ServerPlayer player, Session session) {
		if (session.ticks > LOOK_TICKS || session.startPitch >= LOOK_PITCH) {
			return;
		}
		float step = eased(session.ticks) - eased(session.ticks - 1);
		player.connection.send(new ClientboundPlayerRotationPacket(0, true, (LOOK_PITCH - session.startPitch) * step, true));
	}

	/** How far along the tilt is after `tick` ticks, 0..1, slowing into the end. */
	private static float eased(int tick) {
		float t = Mth.clamp(tick / (float) LOOK_TICKS, 0, 1);
		return 1 - (1 - t) * (1 - t);
	}

	/** Checks the anchor again: it may have been broken or drained while the player sank. True if they were taken. */
	private static boolean finish(ServerPlayer player) {
		var resolution = AnchorBinding.resolve(player);
		if (resolution instanceof AnchorBinding.Ready ready && AnchorBinding.consume(ready.level(), ready.anchor())) {
			player.teleport(new TeleportTransition(
					ready.level(), ready.standUp(), Vec3.ZERO, ready.yaw(), 0, TeleportTransition.PLAY_PORTAL_SOUND
			));
			player.resetFallDistance();
			var at = ready.standUp();
			ready.level().sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.05);
			return true;
		}
		hint(player, resolution instanceof AnchorBinding.Ready ? new AnchorBinding.Empty() : resolution);
		return false;
	}

	private static void hint(ServerPlayer player, AnchorBinding.Resolution resolution) {
		var message = switch (resolution) {
			case AnchorBinding.Empty empty -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.empty", "Your void anchor is empty"
			);
			case AnchorBinding.Gone gone -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.gone", "Your void anchor is gone"
			);
			case AnchorBinding.NotInEnd notInEnd -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.gone", "Your void anchor is gone"
			);
			case AnchorBinding.Blocked blocked -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.blocked", "Your void anchor is blocked"
			);
			case AnchorBinding.Unbound unbound -> null;
			case AnchorBinding.Ready ready -> null;
		};
		if (message != null) {
			player.sendOverlayMessage(message.withStyle(ChatFormatting.RED));
		}
	}

}
