package se.datasektionen.mc.cutscenes.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.rotation_ref.*;

public class RotationRefRegistry {

	public static final Registry<RotationRefType<?>> REGISTRY = FabricRegistryBuilder.<RotationRefType<?>>createSimple(
			RegistryKey.ofRegistry(Cutscenes.getID("rotation_ref"))
	).buildAndRegister();

	public static final Codec<RotationRef> CODEC = REGISTRY.getCodec().dispatch(
			RotationRef::getType, RotationRefType::codec
	);

	public static final RotationRefType<FixedRot> FIXED = register("fixed", FixedRot.CODEC);
	public static final RotationRefType<TowardsTarget> TOWARDS_TARGET = register("towards_target", TowardsTarget.CODEC);
	public static final RotationRefType<CopyFromEntity> COPY_FROM_ENTITY = register("copy_from_entity", CopyFromEntity.CODEC);
	public static final RotationRefType<FirstValidRot> FIRST_VALID = register("first_valid", FirstValidRot.CODEC);

	public static void init() {

	}

	private static <T extends RotationRef> RotationRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new RotationRefType<>(codec));
	}


}
