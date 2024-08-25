package se.datasektionen.mc.metacraft_core.entity.ai;

import com.mojang.serialization.Codec;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.GlobalPos;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.Optional;

public class METAcraftMemoryModules {

	public static final MemoryModuleType<GlobalPos> NEAREST_OXYGEN = register("nearest_oxygen", GlobalPos.CODEC);
	public static final MemoryModuleType<GlobalPos> MOVE_TARGET = register("move_target", GlobalPos.CODEC);

	public static void init() {

	}

	private static <U> MemoryModuleType<U> register(String id, Codec<U> codec) {
		return Registry.register(
				Registries.MEMORY_MODULE_TYPE, METAcraftCore.getID(id), new MemoryModuleType<U>(Optional.of(codec))
		);
	}

}
