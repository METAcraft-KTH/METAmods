package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.function.Consumer;

public class GiveAttributeAttack implements Attack {

	public static final MapCodec<GiveAttributeAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RegistryElementCodec.of(
							RegistryKeys.ATTRIBUTE, Registries.ATTRIBUTE.getCodec()
					).fieldOf("attribute").forGetter(a -> a.attribute),
					EntityAttributeModifier.CODEC.fieldOf("modifier").forGetter(a -> a.modifier)
			).apply(instance, GiveAttributeAttack::new)
	);

	private final RegistryEntry<EntityAttribute> attribute;

	private final EntityAttributeModifier modifier;

	public GiveAttributeAttack(
			RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier
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
				attribute.addTemporaryModifier(modifier);
			}
		});
	}

	private void forAttributeInstances(BossContext<?> ctx, Consumer<EntityAttributeInstance> function) {
		for (var target : ctx.boss().getLivingTargets()) {
			switch (target.getAttributeInstance(attribute)) {
				case null -> {}
				case EntityAttributeInstance instance -> {
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
