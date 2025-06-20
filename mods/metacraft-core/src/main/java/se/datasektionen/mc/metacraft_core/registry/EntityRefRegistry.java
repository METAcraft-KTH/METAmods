package se.datasektionen.mc.metacraft_core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.entity_ref.*;

public class EntityRefRegistry {

	public static final Registry<EntityRefType<?>> REGISTRY = FabricRegistryBuilder.<EntityRefType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftCore.getID("entity_ref"))
	).buildAndRegister();

	public static final Codec<EntityRef> CODEC = REGISTRY.getCodec().dispatch(
			EntityRef::getType, EntityRefType::codec
	);

	public static final EntityRefType<SelfRef> SELF = register("self", SelfRef.CODEC);
	public static final EntityRefType<UUIDRef> UUID = register("uuid", UUIDRef.CODEC);
	public static final EntityRefType<SelectorRef> SELECTOR = register("selector", SelectorRef.CODEC);
	public static final EntityRefType<ConditionalEntityRef> CONDITIONAL = register("conditional", ConditionalEntityRef.CODEC);

	public static void init() {

	}

	private static <T extends EntityRef> EntityRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new EntityRefType<>(codec));
	}


}
