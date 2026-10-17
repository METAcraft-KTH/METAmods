package nu.metacraft.nether_rifts.rift;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.nether_rifts.RiftMessages;
import nu.metacraft.nether_rifts.RiftWorldState;

/**
 * After the realm is stabilised, a closed crying-obsidian frame (any shape) lit with flint and steel or a
 * fire charge opens a rift on purpose; its raid ends with the frame crumbling to nothing. Off during rift
 * mode, so the crying obsidian a won raid leaves behind can't be relit straight away.
 */
public final class CryingIgnition {

	private CryingIgnition() {}

	public static InteractionResult tryOpen(ServerLevel level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
		if (level.dimension() != Level.OVERWORLD || RiftWorldState.get(level.getServer()).riftMode()) {
			return InteractionResult.PASS;
		}
		if (!level.getBlockState(hit.getBlockPos()).is(Blocks.CRYING_OBSIDIAN)) {
			return InteractionResult.PASS;
		}
		var start = hit.getBlockPos().relative(hit.getDirection());
		var shape = RiftShape.fill(level, start, RiftShape.FrameTest.of(s -> s.is(Blocks.CRYING_OBSIDIAN)));
		if (shape.isEmpty()) {
			return InteractionResult.PASS;
		}
		var rift = RiftData.get(level).open(level, shape.get(), true);
		RiftMessages.warn(level, rift);
		Ignition.use(player, hand, stack);
		return InteractionResult.SUCCESS_SERVER;
	}
}
