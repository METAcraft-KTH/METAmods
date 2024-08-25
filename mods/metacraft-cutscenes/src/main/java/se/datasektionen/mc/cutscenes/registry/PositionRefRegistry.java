package se.datasektionen.mc.cutscenes.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.position_ref.AtEntityRef;
import se.datasektionen.mc.cutscenes.position_ref.Fixed;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.position_ref.PositionRefType;

public class PositionRefRegistry {

	public static final Registry<PositionRefType<?>> REGISTRY = FabricRegistryBuilder.<PositionRefType<?>>createSimple(
			RegistryKey.ofRegistry(Cutscenes.getID("position_ref"))
	).buildAndRegister();

	public static final Codec<PositionRef> CODEC = REGISTRY.getCodec().dispatch(
			PositionRef::getType, PositionRefType::codec
	);

	public static final PositionRefType<AtEntityRef> ENTITY = register("entity", AtEntityRef.CODEC);
	public static final PositionRefType<Fixed> FIXED = register("fixed", Fixed.CODEC);

	public static void init() {

	}

	private static <T extends PositionRef> PositionRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new PositionRefType<>(codec));
	}


}
