package nu.metacraft.season_4.entity.ai;

import com.mojang.serialization.Codec;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Unit;
import nu.metacraft.season_4.Season4;

import java.util.Optional;

public class Season4MemoryModules {

	public static final MemoryModuleType<LivingEntity> SWOOP_TARGET = register("swoop_target");
	public static final MemoryModuleType<Unit> STUNNED = register("stunned", Unit.CODEC);

	public static void init() {

	}

	private static <U> MemoryModuleType<U> register(String id, Codec<U> codec) {
		return Registry.register(Registries.MEMORY_MODULE_TYPE, Season4.getID(id), new MemoryModuleType<>(Optional.of(codec)));
	}

	private static <U> MemoryModuleType<U> register(String id) {
		return Registry.register(Registries.MEMORY_MODULE_TYPE, Season4.getID(id), new MemoryModuleType<>(Optional.empty()));
	}

}
