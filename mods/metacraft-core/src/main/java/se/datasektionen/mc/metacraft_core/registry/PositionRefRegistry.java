package se.datasektionen.mc.metacraft_core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.position_ref.*;

public class PositionRefRegistry {

	public static final Registry<PositionRefType<?>> REGISTRY = FabricRegistryBuilder.<PositionRefType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftCore.getID("position_ref"))
	).buildAndRegister();

	public static final Codec<PositionRef> CODEC = REGISTRY.getCodec().dispatch(
			PositionRef::getType, PositionRefType::codec
	);

	public static final PositionRefType<AtEntityRef> ENTITY = register("entity", AtEntityRef.CODEC);
	public static final PositionRefType<Fixed> FIXED = register("fixed", Fixed.CODEC);
	public static final PositionRefType<FirstValidPos> FIRST_VALID = register("first_valid", FirstValidPos.CODEC);
	public static final PositionRefType<RandomRangeWithGravity> RANDOM_RANGE_WITH_GRAVITY = register("random_range_with_gravity", RandomRangeWithGravity.CODEC);
	public static final PositionRefType<RandomChoice> RANDOM_CHOICE = register("random_choice", RandomChoice.CODEC);
	public static final PositionRefType<WithTries> WITH_TRIES = register("with_tries", WithTries.CODEC);
	public static final PositionRefType<Nearest> NEAREST = register("nearest", Nearest.CODEC);
	public static final PositionRefType<Cached> CACHED = register("cached", Cached.CODEC);

	public static void init() {

	}

	private static <T extends PositionRef> PositionRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new PositionRefType<>(codec));
	}


}
