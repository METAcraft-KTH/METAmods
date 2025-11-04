package nu.metacraft.cutscenes.compat.resource_pack;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

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
		return Registry.register(TransitionConfigRegistry.REGISTRY, ResourceLocation.withDefaultNamespace(id), new TransitionConfigType<>(codec));
	}

	private static <T extends Transition> TransitionType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(TransitionRegistry.REGISTRY, ResourceLocation.withDefaultNamespace(id), new TransitionType<>(codec));
	}

}
