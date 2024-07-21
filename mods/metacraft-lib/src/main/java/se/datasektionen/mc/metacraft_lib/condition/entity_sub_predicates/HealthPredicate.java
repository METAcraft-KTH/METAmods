package se.datasektionen.mc.metacraft_lib.condition.entity_sub_predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorItemEntity;

public class HealthPredicate implements EntitySubPredicate {

	public static final MapCodec<HealthPredicate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					NumberRange.DoubleRange.CODEC.fieldOf("health_range").forGetter(c -> c.healthRange),
					Codec.BOOL.optionalFieldOf("fraction_mode", false).forGetter(c -> c.fractionMode)
			).apply(instance, HealthPredicate::new)
	);

	private final NumberRange.DoubleRange healthRange;
	private final boolean fractionMode;

	public HealthPredicate(NumberRange.DoubleRange healthRange, boolean fractionMode) {
		this.healthRange = healthRange;
		this.fractionMode = fractionMode;
	}

	@Override
	public MapCodec<? extends EntitySubPredicate> getCodec() {
		return CODEC;
	}

	@Override
	public boolean test(Entity entity, ServerWorld world, @Nullable Vec3d pos) {
		if (entity instanceof LivingEntity living) {
			return fractionMode ? healthRange.test(living.getHealth() / living.getMaxHealth()) : healthRange.test(living.getHealth());
		}
		if (entity instanceof AccessorItemEntity item) {
			return fractionMode ? healthRange.test(item.getHealth() / 5.0f) : healthRange.test(item.getHealth());
		}
		return false;
	}
}
