package se.metacraft.bosses.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import se.metacraft.bosses.item.components.BossComponents;
import xyz.nucleoid.packettweaker.PacketContext;

public class EvokerFangsWand extends Item implements PolymerItem {

	public EvokerFangsWand(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		var stack = user.getStackInHand(hand);
		double maxRange = stack.contains(BossComponents.MAX_RANGE) ? stack.get(BossComponents.MAX_RANGE) : 64;
		Vec3d eyePos = user.getPos().add(0, user.getEyeHeight(user.getPose()), 0);
		Vec3d facingVector = user.getRotationVector().multiply(maxRange);
		Vec3d endPos = eyePos.add(facingVector);

		HitResult result = ProjectileUtil.raycast(user, eyePos, endPos, user.getBoundingBox().stretch(facingVector), entity -> true, facingVector.length());
		if (result == null) {
			result = user.getWorld().raycast(new RaycastContext(eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		}
		//Credits to Mojang.
		Vec3d target = result.getPos().add(user.getRotationVector());
		double minYDist = Math.min(target.getY(), user.getY());
		double maxYDist = Math.max(target.getY(), user.getY()) + 1.0;
		float yawToTarget = (float) MathHelper.atan2(target.getZ() - user.getZ(), target.getX() - user.getX());
		if (user.isSneaking()) {
			float yaw;
			int i;
			for (i = 0; i < 5; ++i) {
				yaw = yawToTarget + (float)i * (float)Math.PI * 0.4f;
				this.conjureFangs(user, user.getX() + (double)MathHelper.cos(yaw) * 1.5, user.getZ() + (double)MathHelper.sin(yaw) * 1.5, minYDist, maxYDist, yaw, 0);
			}
			for (i = 0; i < 8; ++i) {
				yaw = yawToTarget + (float)i * (float)Math.PI * 2.0f / 8.0f + 1.2566371f;
				this.conjureFangs(user, user.getX() + (double)MathHelper.cos(yaw) * 2.5, user.getZ() + (double)MathHelper.sin(yaw) * 2.5, minYDist, maxYDist, yaw, 3);
			}
		} else {
			for (int i = 0; i < maxRange; ++i) {
				double h = 1.25 * (double)(i + 1);
				this.conjureFangs(user, user.getX() + (double)MathHelper.cos(yawToTarget) * h, user.getZ() + (double)MathHelper.sin(yawToTarget) * h, minYDist, maxYDist, yawToTarget, i);
			}
		}
		return ActionResult.SUCCESS_SERVER.noIncrementStat();
	}

	public void conjureFangs(PlayerEntity owner, double x, double z, double maxY, double y, float yaw, int warmup) {
		//Credits to Mojang.
		BlockPos blockPos = BlockPos.ofFloored(x, y, z);
		boolean bl = false;
		double d = 0.0;
		do {
			VoxelShape voxelShape;
			BlockPos blockPos2;
			if (!owner.getWorld().getBlockState(blockPos2 = blockPos.down()).isSideSolidFullSquare(owner.getWorld(), blockPos2, Direction.UP)) continue;
			if (!owner.getWorld().isAir(blockPos) && !(voxelShape = owner.getWorld().getBlockState(blockPos).getCollisionShape(owner.getWorld(), blockPos)).isEmpty()) {
				d = voxelShape.getMax(Direction.Axis.Y);
			}
			bl = true;
			break;
		} while ((blockPos = blockPos.down()).getY() >= MathHelper.floor(maxY) - 1);
		if (bl) {
			var entity = new EvokerFangsEntity(owner.getWorld(), x, (double)blockPos.getY() + d, z, yaw, warmup, owner);
			entity.setOwner(owner);
			if (owner.getScoreboardTeam() != null) {
				owner.getServer().getScoreboard().addScoreHolderToTeam(entity.getNameForScoreboard(), owner.getScoreboardTeam());
			}
			owner.getWorld().spawnEntity(entity);
		}
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
		return Items.BLAZE_ROD;
	}
}
