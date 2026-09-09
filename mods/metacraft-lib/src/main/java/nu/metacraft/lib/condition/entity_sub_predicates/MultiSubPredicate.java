package nu.metacraft.lib.condition.entity_sub_predicates;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public abstract class MultiSubPredicate implements EntitySubPredicate {

	private static <T> MapCodec<T> fromCodec(Codec<T> codec) {
		if (codec instanceof MapCodec.MapCodecCodec<T>(MapCodec<T> mapCodec)) {
			return mapCodec;
		} else {
			return codec.fieldOf("value");
		}
	}

	private static <T extends EntitySubPredicate> MapCodec<Pair<? extends Codec<T>, T>> fixTheCodec(Codec<T> codec) {
		return fromCodec(codec).xmap(
				v -> Pair.of(codec, v),
				Pair::getSecond
		);
	}

	private static final Codec<
			Pair<? extends Codec<? extends EntitySubPredicate>,
			? extends EntitySubPredicate>
	> ENTITY_SUB_PREDICATE_ENTRY = BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE.byNameCodec().dispatch(
			Pair::getFirst, MultiSubPredicate::fixTheCodec
	);

	protected static <T extends MultiSubPredicate> Codec<T> createCodec(
			Function<List<Pair<? extends Codec<? extends EntitySubPredicate>, ? extends EntitySubPredicate>>, T> creator
	) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.lazyInitialized(
								() -> ENTITY_SUB_PREDICATE_ENTRY.listOf()
						).fieldOf(
								"children"
						).forGetter(
								a -> a.subPredicates
						)
				).apply(instance, creator)
		);
	}

	protected final List<Pair<? extends Codec<? extends EntitySubPredicate>, ? extends EntitySubPredicate>> subPredicates;

	public MultiSubPredicate(List<Pair<? extends Codec<? extends EntitySubPredicate>, ? extends EntitySubPredicate>> subPredicates) {
		this.subPredicates = subPredicates;
	}

	protected Stream<EntitySubPredicate> subPredicates() {
		return subPredicates.stream().map(Pair::getSecond);
	}

	public static class AndSubPredicate extends MultiSubPredicate {

		public static final Codec<AndSubPredicate> CODEC = createCodec(AndSubPredicate::new);

		public AndSubPredicate(List<Pair<? extends Codec<? extends EntitySubPredicate>, ? extends EntitySubPredicate>> subPredicates) {
			super(subPredicates);
		}

		@Override
		public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
			return subPredicates().allMatch(sub -> sub.matches(entity, world, pos));
		}
	}

	public static class OrSubPredicate extends MultiSubPredicate {

		public static final Codec<OrSubPredicate> CODEC = createCodec(OrSubPredicate::new);

		public OrSubPredicate(List<Pair<? extends Codec<? extends EntitySubPredicate>, ? extends EntitySubPredicate>> subPredicates) {
			super(subPredicates);
		}

		@Override
		public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
			return subPredicates().anyMatch(sub -> sub.matches(entity, world, pos));
		}
	}
}
