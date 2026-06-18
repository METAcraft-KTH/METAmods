package nu.metacraft.lib.condition.entity_sub_predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class IsMovingPredicate implements EntitySubPredicate {

	private static final IsMovingPredicate INSTANCE = new IsMovingPredicate();

	public static final Codec<IsMovingPredicate> CODEC = MapCodec.unit(INSTANCE).codec();

	public static IsMovingPredicate getInstance() {
		return INSTANCE;
	}

	private IsMovingPredicate() {}

	@Override
	public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
		if (entity instanceof Mob mob) {
			return mob.getMoveControl().hasWanted();
		}
		return !entity.getDeltaMovement().equals(Vec3.ZERO);
	}
}
