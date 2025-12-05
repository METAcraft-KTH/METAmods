package nu.metacraft.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.entity_ref.EntityRefType;
import nu.metacraft.core.registry.EntityRefRegistry;

public class EntityRefs {

	public static final EntityRefType<CutsceneRef> CUTSCENE = register("cutscene", CutsceneRef.CODEC);
	public static final EntityRefType<PlayerDummy> PLAYER_DUMMY = register("player_dummy", PlayerDummy.CODEC);

	public static void init() {

	}

	private static <T extends EntityRef> EntityRefType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(EntityRefRegistry.REGISTRY, Identifier.withDefaultNamespace(id), new EntityRefType<>(codec));
	}

}
