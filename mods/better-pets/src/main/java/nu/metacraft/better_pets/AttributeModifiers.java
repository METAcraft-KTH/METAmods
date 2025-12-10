package nu.metacraft.better_pets;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeModifiers {

	public static final AttributeModifier BABY_PARROT = new AttributeModifier(
			BetterPets.getID("baby_parrot"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
	);

}
