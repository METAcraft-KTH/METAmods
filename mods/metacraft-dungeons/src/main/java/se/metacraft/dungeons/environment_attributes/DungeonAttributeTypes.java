package se.metacraft.dungeons.environment_attributes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.attribute.AttributeType;
import se.metacraft.core.util.TeleportPredicate;
import se.metacraft.dungeons.METAcraftDungeons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DungeonAttributeTypes {


	public static final AttributeType<@NotNull List<@NotNull TeleportPredicate>> TELEPORT_PREDICATES = register(
			"teleport_predicates", AttributeType.ofNotInterpolated(TeleportPredicate.CODEC.listOf())
	);



	public static void init() {

	}

	private static <Value> AttributeType<@NotNull Value> register(String string, AttributeType<@NotNull Value> attributeType) {
		Registry.register(BuiltInRegistries.ATTRIBUTE_TYPE, METAcraftDungeons.getID(string), attributeType);
		return attributeType;
	}

}
