package se.datasektionen.mc.metacraft_lib.condition.entity_sub_predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public abstract class MultiSubPredicate implements EntitySubPredicate {

	protected static <T extends MultiSubPredicate> MapCodec<T> createCodec(
			Function<List<EntitySubPredicate>, T> creator
	) {
		return RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.lazyInitialized(
								EntitySubPredicate.CODEC::listOf
						).fieldOf(
								"children"
						).forGetter(
								a -> a.subPredicates
						)
				).apply(instance, creator)
		);
	}

	protected final List<EntitySubPredicate> subPredicates;

	public MultiSubPredicate(List<EntitySubPredicate> subPredicates) {
		this.subPredicates = subPredicates;
	}

	public static class AndSubPredicate extends MultiSubPredicate {

		public static final MapCodec<AndSubPredicate> CODEC = createCodec(AndSubPredicate::new);

		public AndSubPredicate(List<EntitySubPredicate> subPredicates) {
			super(subPredicates);
		}

		@Override
		public MapCodec<? extends EntitySubPredicate> getCodec() {
			return CODEC;
		}

		@Override
		public boolean test(Entity entity, ServerWorld world, @Nullable Vec3d pos) {
			return subPredicates.stream().allMatch(sub -> sub.test(entity, world, pos));
		}
	}

	public static class OrSubPredicate extends MultiSubPredicate {

		public static final MapCodec<OrSubPredicate> CODEC = createCodec(OrSubPredicate::new);

		public OrSubPredicate(List<EntitySubPredicate> subPredicates) {
			super(subPredicates);
		}

		@Override
		public MapCodec<? extends EntitySubPredicate> getCodec() {
			return CODEC;
		}

		@Override
		public boolean test(Entity entity, ServerWorld world, @Nullable Vec3d pos) {
			return subPredicates.stream().anyMatch(sub -> sub.test(entity, world, pos));
		}
	}
}
