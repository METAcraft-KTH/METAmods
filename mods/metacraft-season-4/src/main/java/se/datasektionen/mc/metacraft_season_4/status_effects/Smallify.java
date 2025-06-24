package se.datasektionen.mc.metacraft_season_4.status_effects;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Formatting;
import se.datasektionen.mc.metacraft_season_4.Season4;

public class Smallify extends StatusEffect implements PolymerStatusEffect {
	protected Smallify() {
		super(StatusEffectCategory.HARMFUL, Formatting.LIGHT_PURPLE.getColorValue());
		var id = Season4.getID("effect.smallify");
		addAttributeModifier(
				EntityAttributes.SCALE, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.MOVEMENT_SPEED, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.ATTACK_KNOCKBACK, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.MOVEMENT_EFFICIENCY, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.WATER_MOVEMENT_EFFICIENCY, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.FALL_DAMAGE_MULTIPLIER, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.SAFE_FALL_DISTANCE, id, 0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.ATTACK_KNOCKBACK, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.ATTACK_DAMAGE, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.ENTITY_INTERACTION_RANGE, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.BLOCK_INTERACTION_RANGE, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.JUMP_STRENGTH, id, 0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.MINING_EFFICIENCY, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.SUBMERGED_MINING_SPEED, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.STEP_HEIGHT, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.SNEAKING_SPEED, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
		addAttributeModifier(
				EntityAttributes.OXYGEN_BONUS, id, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);

	}
}
