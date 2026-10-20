package nu.metacraft.qol.void_anchor;

import nu.metacraft.qol.Qol;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.qol.void_anchor.block.VoidAnchorBlock;
import org.jspecify.annotations.Nullable;

/** Which void anchor a player is bound to, and where it would take them. */
public final class AnchorBinding {

	public static final AttachmentType<GlobalPos> ANCHOR = AttachmentRegistry.create(
			Qol.getID("void_anchor"), builder -> builder.persistent(GlobalPos.CODEC).copyOnDeath()
	);

	public sealed interface Resolution permits Unbound, NotInEnd, Gone, Empty, Blocked, Ready {}

	public record Unbound() implements Resolution {}

	public record NotInEnd() implements Resolution {}

	public record Gone() implements Resolution {}

	public record Empty() implements Resolution {}

	public record Blocked() implements Resolution {}

	public record Ready(ServerLevel level, BlockPos anchor, Vec3 standUp, float yaw) implements Resolution {}

	private AnchorBinding() {}

	public static void init() {

	}

	public static void bind(ServerPlayer player, GlobalPos anchor) {
		player.setAttached(ANCHOR, anchor);
	}

	public static @Nullable GlobalPos get(ServerPlayer player) {
		return player.getAttached(ANCHOR);
	}

	/** Where the player's anchor would take them now. Loads the anchor's chunk if it must; rescues are rare. */
	public static Resolution resolve(ServerPlayer player) {
		var bound = get(player);
		if (bound == null) {
			return new Unbound();
		}
		if (bound.dimension() != Level.END) {
			return new NotInEnd();
		}
		var level = player.level().getServer().getLevel(Level.END);
		if (level == null) {
			return new NotInEnd();
		}
		var pos = bound.pos();
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof VoidAnchorBlock)) {
			return new Gone();
		}
		if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
			return new Empty();
		}
		return RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, level, pos)
				.<Resolution>map(standUp -> new Ready(level, pos, standUp, lookAtYaw(standUp, pos)))
				.orElseGet(Blocked::new);
	}

	/** Takes one charge if the anchor is still there and has one. */
	public static boolean consume(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof VoidAnchorBlock)) {
			return false;
		}
		int charge = state.getValue(RespawnAnchorBlock.CHARGE);
		if (charge == 0) {
			return false;
		}
		level.setBlockAndUpdate(pos, state.setValue(RespawnAnchorBlock.CHARGE, charge - 1));
		return true;
	}

	/** The yaw that faces the anchor from where the player stands up, as vanilla's respawn does. */
	static float lookAtYaw(Vec3 from, BlockPos anchor) {
		var d = Vec3.atBottomCenterOf(anchor).subtract(from).normalize();
		return (float) Mth.wrapDegrees(Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90.0);
	}

}
