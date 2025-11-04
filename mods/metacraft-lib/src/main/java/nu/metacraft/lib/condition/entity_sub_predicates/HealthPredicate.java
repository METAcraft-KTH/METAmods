package nu.metacraft.lib.condition.entity_sub_predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.mixin.ItemEntityAccessor;

public class HealthPredicate implements EntitySubPredicate {

	public static final MapCodec<HealthPredicate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MinMaxBounds.Doubles.CODEC.fieldOf("health_range").forGetter(c -> c.healthRange),
					Codec.BOOL.optionalFieldOf("fraction_mode", false).forGetter(c -> c.fractionMode)
			).apply(instance, HealthPredicate::new)
	);

	private final MinMaxBounds.Doubles healthRange;
	private final boolean fractionMode;

	public HealthPredicate(MinMaxBounds.Doubles healthRange, boolean fractionMode) {
		this.healthRange = healthRange;
		this.fractionMode = fractionMode;
	}

	@Override
	public MapCodec<? extends EntitySubPredicate> codec() {
		return CODEC;
	}

	@Override
	public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
		if (entity instanceof LivingEntity living) {
			return fractionMode ? healthRange.matches(living.getHealth() / living.getMaxHealth()) : healthRange.matches(living.getHealth());
		}
		if (entity instanceof ItemEntityAccessor item) {
			return fractionMode ? healthRange.matches(item.getHealth() / 5.0f) : healthRange.matches(item.getHealth());
		}
		return false;
	}
}
