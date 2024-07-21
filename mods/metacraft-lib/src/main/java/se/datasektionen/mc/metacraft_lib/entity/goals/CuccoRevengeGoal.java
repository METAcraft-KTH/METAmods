package se.datasektionen.mc.metacraft_lib.entity.goals;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import se.datasektionen.mc.metacraft_lib.extensions.ChickenExtensions;

public class CuccoRevengeGoal extends RevengeGoal {
	public CuccoRevengeGoal(ChickenEntity mob, Class<?>... noRevengeTypes) {
		super(mob, noRevengeTypes);
		this.setGroupRevenge();
	}

	@Override
	public boolean canStart() {
		if (mob.getTarget() != null && mob.getTarget().isDead()) {
			mob.setTarget(null);
		}
		return ((ChickenExtensions) mob).metacraft_lib$isCucco() && super.canStart();
	}

	@Override
	public void callSameTypeForRevenge() {
		final int minAttackers = ((ChickenExtensions) mob).metacraft_lib$getReinforcementCount();
		double range = getFollowRange();
		var mobs = mob.getWorld().getOtherEntities(mob, new Box(
				mob.getX() - range, mob.getY() - range, mob.getZ() - range,
				mob.getX() + range, mob.getY() + range, mob.getZ() + range
		), entity -> entity instanceof ChickenEntity && ((ChickenExtensions) entity).metacraft_lib$isCucco());
		if (mobs.size() < minAttackers) {
			int toSpawn = minAttackers - mobs.size();
			((ChickenExtensions) mob).metacraft_lib$setReinforcementCount(minAttackers - toSpawn);
			for (int i = 0; i < toSpawn; i++) {
				var chicken = EntityType.CHICKEN.create(mob.getWorld());
				chicken.initialize(
						(ServerWorldAccess) mob.getWorld(),
						mob.getWorld().getLocalDifficulty(mob.getBlockPos()),
						SpawnReason.REINFORCEMENT, null
				);
				((ChickenExtensions) chicken).metacraft_lib$setCucco(true);
				((ChickenExtensions) chicken).metacraft_lib$setReinforcementCount(0);
				double x = mob.getX() + mob.getWorld().getRandom().nextGaussian() * range;
				double z = mob.getZ() + mob.getWorld().getRandom().nextGaussian() * range;
				int y = mob.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
				chicken.refreshPositionAndAngles(x, y, z, 0, 0);
				mob.getWorld().spawnEntity(chicken);
			}
		}
		super.callSameTypeForRevenge();
	}

	@Override
	protected double getFollowRange() {
		return super.getFollowRange();
	}
}
