package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryFileCodec;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class GiveAttributeAttack implements Attack {

	public static final MapCodec<GiveAttributeAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RegistryFileCodec.create(
							Registries.ATTRIBUTE, BuiltInRegistries.ATTRIBUTE.byNameCodec(), false
					).fieldOf("attribute").forGetter(a -> a.attribute),
					AttributeModifier.CODEC.fieldOf("modifier").forGetter(a -> a.modifier)
			).apply(instance, GiveAttributeAttack::new)
	);

	private final Holder<Attribute> attribute;

	private final AttributeModifier modifier;

	public GiveAttributeAttack(
			Holder<Attribute> attribute, AttributeModifier modifier
	) {
		this.attribute = attribute;
		this.modifier = modifier;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		giveAttribute(ctx);
	}

	@Override
	public void tick(BossContext<?> ctx) {
		giveAttribute(ctx);
	}

	public void giveAttribute(BossContext<?> ctx) {
		forAttributeInstances(ctx, attribute -> {
			if (!attribute.hasModifier(modifier.id())) {
				attribute.addTransientModifier(modifier);
			}
		});
	}

	private void forAttributeInstances(BossContext<?> ctx, Consumer<AttributeInstance> function) {
		for (var target : ctx.boss().getLivingTargets()) {
			switch (target.getAttribute(attribute)) {
				case null -> {}
				case AttributeInstance instance -> {
					function.accept(instance);
				}
			}
		}
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		forAttributeInstances(ctx, attribute -> {
			attribute.removeModifier(modifier);
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.GIVE_ATTRIBUTE;
	}
}
