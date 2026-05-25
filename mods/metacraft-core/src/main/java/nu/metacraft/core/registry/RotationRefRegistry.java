package nu.metacraft.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.rotation_ref.*;

public class RotationRefRegistry {

	public static final Registry<RotationRefType<?>> REGISTRY = FabricRegistryBuilder.<RotationRefType<?>>create(
			ResourceKey.createRegistryKey(METAcraftCore.getID("rotation_ref"))
	).buildAndRegister();

	public static final Codec<RotationRef> CODEC = REGISTRY.byNameCodec().dispatch(
			RotationRef::getType, RotationRefType::codec
	);

	public static final RotationRefType<FixedRot> FIXED = register("fixed", FixedRot.CODEC);
	public static final RotationRefType<TowardsTarget> TOWARDS_TARGET = register("towards_target", TowardsTarget.CODEC);
	public static final RotationRefType<CopyFromEntity> COPY_FROM_ENTITY = register("copy_from_entity", CopyFromEntity.CODEC);
	public static final RotationRefType<FirstValidRot> FIRST_VALID = register("first_valid", FirstValidRot.CODEC);

	public static void init() {

	}

	private static <T extends RotationRef> RotationRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), new RotationRefType<>(codec));
	}


}
