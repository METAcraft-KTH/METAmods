package nu.metacraft.core.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.core.METAcraftCore;

public class PortalTargetRegistry {

	public static final Registry<PortalTargetType<?>> REGISTRY = FabricRegistryBuilder.<PortalTargetType<?>>createSimple(
			ResourceKey.createRegistryKey(METAcraftCore.getID("portal_target"))
	).buildAndRegister();

	public static final Codec<PortalTarget> CODEC = REGISTRY.byNameCodec().dispatch(PortalTarget::getType, PortalTargetType::codec);

	public static final PortalTargetType<FixedPortalTarget> FIXED = register(
			"fixed", new PortalTargetType<>(FixedPortalTarget.CODEC)
	);

	public static final PortalTargetType<FixedLocalPortalTarget> FIXED_LOCAL = register(
			"fixed_local", new PortalTargetType<>(FixedLocalPortalTarget.CODEC)
	);


	public static final PortalTargetType<EmptyPortalTarget> EMPTY = register(
			"empty", new PortalTargetType<>(EmptyPortalTarget.CODEC)
	);



	private static <T extends PortalTargetType<? extends PortalTarget>> T register(String id, T object) {
		return Registry.register(REGISTRY, ResourceLocation.withDefaultNamespace(id), object);
	}

	public record PortalTargetType<T extends PortalTarget>(MapCodec<T> codec) {

	}

	public static void init() {

	}

}
