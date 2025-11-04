package nu.metacraft.moderation.exile.rules;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.moderation.METAcraftModeration;

public class ZoneRuleRegistry {
	public static final Registry<ZoneRule> REGISTRY = FabricRegistryBuilder.<ZoneRule>createSimple(
			ResourceKey.createRegistryKey(METAcraftModeration.getID("zone_rule"))
	).buildAndRegister();

	public static final ZoneRule preventInteraction = register("prevent_interaction_outside", new PreventInteraction());


	private static <T extends ZoneRule> T register(String id, T value) {
		return Registry.register(REGISTRY, ResourceLocation.withDefaultNamespace(id), value);
	}

	public static void init() {

	}

}
