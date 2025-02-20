package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;

import java.util.Optional;
import java.util.stream.Stream;

public class ConditionalEntityRef implements EntityRef {

	public static final MapCodec<ConditionalEntityRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					LootCondition.CODEC.fieldOf("condition").forGetter(t -> t.condition),
					Codec.lazyInitialized(() -> EntityRefRegistry.CODEC).fieldOf("entity").forGetter(t -> t.ref)
			).apply(instance, ConditionalEntityRef::new)
	);

	private final LootCondition condition;
	private final EntityRef ref;

	public ConditionalEntityRef(LootCondition condition, EntityRef ref) {
		this.condition = condition;
		this.ref = ref;
	}

	@Override
	public Stream<? extends Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return ref.get(player, cutsceneInstance).filter(
				e -> {
					LootWorldContext lootWorldContext = new LootWorldContext.Builder(cutsceneInstance.getCutsceneWorld())
							.add(LootContextParameters.THIS_ENTITY, e)
							.add(LootContextParameters.ORIGIN, e.getPos())
							.build(LootContextTypes.SELECTOR);
					return condition.test(
							new LootContext.Builder(lootWorldContext)
									.random(cutsceneInstance.getRandom())
									.build(Optional.empty())
					);
				}
		);
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.CONDITIONAL;
	}
}
