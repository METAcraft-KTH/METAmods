package se.datasektionen.mc.cutscenes.compat.resource_pack;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

public class ResourcePackTransition {

	public static final TransitionType<SetResourcePackTransition> RESOURCE_PACK = register(
			"resource_pack", SetResourcePackTransition.CODEC
	);
	public static final TransitionConfigType<SetResourcePackTransition.Config> RESOURCE_PACK_CONFIG = registerConfig(
			"resource_pack", SetResourcePackTransition.Config.CODEC
	);

	public static void init() {

	}

	private static <T extends TransitionConfig> TransitionConfigType<T> registerConfig(String id, MapCodec<T> codec) {
		return Registry.register(TransitionConfigRegistry.REGISTRY, Identifier.ofVanilla(id), new TransitionConfigType<>(codec));
	}

	private static <T extends Transition> TransitionType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(TransitionRegistry.REGISTRY, Identifier.ofVanilla(id), new TransitionType<>(codec));
	}

}
