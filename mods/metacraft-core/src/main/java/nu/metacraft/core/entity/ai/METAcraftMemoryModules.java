package nu.metacraft.core.entity.ai;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import nu.metacraft.core.METAcraftCore;

import java.util.Optional;

public class METAcraftMemoryModules {

	public static final MemoryModuleType<GlobalPos> NEAREST_OXYGEN = register("nearest_oxygen", GlobalPos.CODEC);
	public static final MemoryModuleType<GlobalPos> MOVE_TARGET = register("move_target", GlobalPos.CODEC);
	public static final MemoryModuleType<Unit> RECOVERING_BREATH = register("recovering_breath", Codec.unit(Unit.INSTANCE));
	public static final MemoryModuleType<Unit> IS_SMART_SHOOTING = register("is_smart_shooting");

	public static void init() {

	}

	private static <U> MemoryModuleType<U> register(String id) {
		return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE, METAcraftCore.getID(id), new MemoryModuleType<>(Optional.empty()));
	}

	private static <U> MemoryModuleType<U> register(String id, Codec<U> codec) {
		return Registry.register(
				BuiltInRegistries.MEMORY_MODULE_TYPE, METAcraftCore.getID(id), new MemoryModuleType<U>(Optional.of(codec))
		);
	}

}
