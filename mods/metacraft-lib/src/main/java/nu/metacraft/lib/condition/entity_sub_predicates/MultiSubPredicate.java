package nu.metacraft.lib.condition.entity_sub_predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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
		public MapCodec<? extends EntitySubPredicate> codec() {
			return CODEC;
		}

		@Override
		public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
			return subPredicates.stream().allMatch(sub -> sub.matches(entity, world, pos));
		}
	}

	public static class OrSubPredicate extends MultiSubPredicate {

		public static final MapCodec<OrSubPredicate> CODEC = createCodec(OrSubPredicate::new);

		public OrSubPredicate(List<EntitySubPredicate> subPredicates) {
			super(subPredicates);
		}

		@Override
		public MapCodec<? extends EntitySubPredicate> codec() {
			return CODEC;
		}

		@Override
		public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
			return subPredicates.stream().anyMatch(sub -> sub.matches(entity, world, pos));
		}
	}
}
