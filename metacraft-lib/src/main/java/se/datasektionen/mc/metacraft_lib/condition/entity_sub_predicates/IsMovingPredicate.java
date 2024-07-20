package se.datasektionen.mc.metacraft_lib.condition.entity_sub_predicates;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class IsMovingPredicate implements EntitySubPredicate {

	private static final IsMovingPredicate INSTANCE = new IsMovingPredicate();

	public static final MapCodec<IsMovingPredicate> CODEC = MapCodec.unit(INSTANCE);

	public static IsMovingPredicate getInstance() {
		return INSTANCE;
	}

	private IsMovingPredicate() {}

	@Override
	public MapCodec<? extends EntitySubPredicate> getCodec() {
		return CODEC;
	}

	@Override
	public boolean test(Entity entity, ServerWorld world, @Nullable Vec3d pos) {
		if (entity instanceof MobEntity mob) {
			return mob.getMoveControl().isMoving();
		}
		return !entity.getVelocity().equals(Vec3d.ZERO);
	}
}
