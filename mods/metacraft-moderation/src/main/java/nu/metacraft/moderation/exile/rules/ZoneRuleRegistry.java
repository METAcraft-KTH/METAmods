package nu.metacraft.moderation.exile.rules;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import nu.metacraft.moderation.METAcraftModeration;

public class ZoneRuleRegistry {
	public static final Registry<ZoneRule> REGISTRY = FabricRegistryBuilder.<ZoneRule>createSimple(
			RegistryKey.ofRegistry(METAcraftModeration.getID("zone_rule"))
	).buildAndRegister();

	public static final ZoneRule preventInteraction = register("prevent_interaction_outside", new PreventInteraction());


	private static <T extends ZoneRule> T register(String id, T value) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), value);
	}

	public static void init() {

	}

}
