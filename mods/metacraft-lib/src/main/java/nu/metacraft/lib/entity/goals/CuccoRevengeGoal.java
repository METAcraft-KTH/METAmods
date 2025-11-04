package nu.metacraft.lib.entity.goals;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import nu.metacraft.lib.extensions.ChickenExtensions;

public class CuccoRevengeGoal extends HurtByTargetGoal {
	public CuccoRevengeGoal(Chicken mob, Class<?>... noRevengeTypes) {
		super(mob, noRevengeTypes);
		this.setAlertOthers();
	}

	@Override
	public boolean canUse() {
		if (mob.getTarget() != null && mob.getTarget().isDeadOrDying()) {
			mob.setTarget(null);
		}
		return ((ChickenExtensions) mob).metacraft_lib$isCucco() && super.canUse();
	}

	@Override
	public void alertOthers() {
		final int minAttackers = ((ChickenExtensions) mob).metacraft_lib$getReinforcementCount();
		double range = getFollowDistance();
		var mobs = mob.level().getEntities(mob, new AABB(
				mob.getX() - range, mob.getY() - range, mob.getZ() - range,
				mob.getX() + range, mob.getY() + range, mob.getZ() + range
		), entity -> entity instanceof Chicken && ((ChickenExtensions) entity).metacraft_lib$isCucco());
		if (mobs.size() < minAttackers) {
			int toSpawn = minAttackers - mobs.size();
			((ChickenExtensions) mob).metacraft_lib$setReinforcementCount(minAttackers - toSpawn);
			for (int i = 0; i < toSpawn; i++) {
				var chicken = EntityType.CHICKEN.create(mob.level(), EntitySpawnReason.REINFORCEMENT);
				chicken.finalizeSpawn(
						(ServerLevelAccessor) mob.level(),
						mob.level().getCurrentDifficultyAt(mob.blockPosition()),
						EntitySpawnReason.REINFORCEMENT, null
				);
				((ChickenExtensions) chicken).metacraft_lib$setCucco(true);
				((ChickenExtensions) chicken).metacraft_lib$setReinforcementCount(0);
				double x = mob.getX() + mob.level().getRandom().nextGaussian() * range;
				double z = mob.getZ() + mob.level().getRandom().nextGaussian() * range;
				int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
				chicken.snapTo(x, y, z, 0, 0);
				mob.level().addFreshEntity(chicken);
			}
		}
		super.alertOthers();
	}

	@Override
	protected double getFollowDistance() {
		return super.getFollowDistance();
	}
}
