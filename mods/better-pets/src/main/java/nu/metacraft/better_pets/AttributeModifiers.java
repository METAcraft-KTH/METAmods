package nu.metacraft.better_pets;

import net.minecraft.entity.attribute.EntityAttributeModifier;

public class AttributeModifiers {

	public static final EntityAttributeModifier BABY_PARROT = new EntityAttributeModifier(
			BetterPets.getID("baby_parrot"), -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
	);

}
