package nu.metacraft.bosses.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import nu.metacraft.bosses.item.components.BossComponents;
import xyz.nucleoid.packettweaker.PacketContext;

public class EvokerFangsWand extends Item implements PolymerItem {

	public EvokerFangsWand(net.minecraft.world.item.Item.Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		var stack = user.getItemInHand(hand);
		double maxRange = stack.has(BossComponents.MAX_RANGE) ? stack.get(BossComponents.MAX_RANGE) : 64;
		Vec3 eyePos = user.position().add(0, user.getEyeHeight(user.getPose()), 0);
		Vec3 facingVector = user.getLookAngle().scale(maxRange);
		Vec3 endPos = eyePos.add(facingVector);

		HitResult result = ProjectileUtil.getEntityHitResult(user, eyePos, endPos, user.getBoundingBox().expandTowards(facingVector), entity -> true, facingVector.length());
		if (result == null) {
			result = user.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
		}
		//Credits to Mojang.
		Vec3 target = result.getLocation().add(user.getLookAngle());
		double minYDist = Math.min(target.y(), user.getY());
		double maxYDist = Math.max(target.y(), user.getY()) + 1.0;
		float yawToTarget = (float) Mth.atan2(target.z() - user.getZ(), target.x() - user.getX());
		if (user.isShiftKeyDown()) {
			float yaw;
			int i;
			for (i = 0; i < 5; ++i) {
				yaw = yawToTarget + (float)i * (float)Math.PI * 0.4f;
				this.conjureFangs(user, user.getX() + (double)Mth.cos(yaw) * 1.5, user.getZ() + (double)Mth.sin(yaw) * 1.5, minYDist, maxYDist, yaw, 0);
			}
			for (i = 0; i < 8; ++i) {
				yaw = yawToTarget + (float)i * (float)Math.PI * 2.0f / 8.0f + 1.2566371f;
				this.conjureFangs(user, user.getX() + (double)Mth.cos(yaw) * 2.5, user.getZ() + (double)Mth.sin(yaw) * 2.5, minYDist, maxYDist, yaw, 3);
			}
		} else {
			for (int i = 0; i < maxRange; ++i) {
				double h = 1.25 * (double)(i + 1);
				this.conjureFangs(user, user.getX() + (double)Mth.cos(yawToTarget) * h, user.getZ() + (double)Mth.sin(yawToTarget) * h, minYDist, maxYDist, yawToTarget, i);
			}
		}
		return InteractionResult.SUCCESS_SERVER.withoutItem();
	}

	public void conjureFangs(Player owner, double x, double z, double maxY, double y, float yaw, int warmup) {
		//Credits to Mojang.
		BlockPos blockPos = BlockPos.containing(x, y, z);
		boolean bl = false;
		double d = 0.0;
		do {
			VoxelShape voxelShape;
			BlockPos blockPos2;
			if (!owner.level().getBlockState(blockPos2 = blockPos.below()).isFaceSturdy(owner.level(), blockPos2, Direction.UP)) continue;
			if (!owner.level().isEmptyBlock(blockPos) && !(voxelShape = owner.level().getBlockState(blockPos).getCollisionShape(owner.level(), blockPos)).isEmpty()) {
				d = voxelShape.max(Direction.Axis.Y);
			}
			bl = true;
			break;
		} while ((blockPos = blockPos.below()).getY() >= Mth.floor(maxY) - 1);
		if (bl) {
			var entity = new EvokerFangs(owner.level(), x, (double)blockPos.getY() + d, z, yaw, warmup, owner);
			entity.setOwner(owner);
			if (owner.getTeam() != null) {
				owner.level().getServer().getScoreboard().addPlayerToTeam(entity.getScoreboardName(), owner.getTeam());
			}
			owner.level().addFreshEntity(entity);
		}
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
		return Items.BLAZE_ROD;
	}
}
