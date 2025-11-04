package nu.metacraft.core.entity_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ConditionalEntityRef implements EntityRef {

	public static final MapCodec<ConditionalEntityRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					LootItemCondition.DIRECT_CODEC.fieldOf("condition").forGetter(t -> t.condition),
					Codec.lazyInitialized(() -> EntityRefRegistry.CODEC).fieldOf("entity").forGetter(t -> t.ref)
			).apply(instance, ConditionalEntityRef::new)
	);

	private final LootItemCondition condition;
	private final EntityRef ref;

	public ConditionalEntityRef(LootItemCondition condition, EntityRef ref) {
		this.condition = condition;
		this.ref = ref;
	}

	@Override
	public Stream<? extends Entity> get(RefContext ctx) {
		return ref.get(ctx).filter(
				e -> {
					LootParams lootWorldContext = new LootParams.Builder(ctx.world())
							.withParameter(LootContextParams.THIS_ENTITY, e)
							.withParameter(LootContextParams.ORIGIN, e.position())
							.create(LootContextParamSets.SELECTOR);
					return condition.test(
							new LootContext.Builder(lootWorldContext)
									.withOptionalRandomSource(ctx.random())
									.create(Optional.empty())
					);
				}
		);
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.CONDITIONAL;
	}
}
