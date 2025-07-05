package nu.metacraft.season_4.status_effects;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Formatting;
import nu.metacraft.season_4.Season4;

public class HealthReduction extends StatusEffect implements PolymerStatusEffect {
	protected HealthReduction() {
		super(StatusEffectCategory.HARMFUL, Formatting.DARK_RED.getColorValue());
		addAttributeModifier(EntityAttributes.MAX_HEALTH, Season4.getID("effect.health_reduction"), -1, EntityAttributeModifier.Operation.ADD_VALUE);
	}
}
