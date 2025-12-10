package nu.metacraft.cutscenes.util.helper;

import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import nu.metacraft.cutscenes.mixin.LightEngineAccessor;
import nu.metacraft.cutscenes.mixin.LevelLightEngineAccessor;

public class LightingHelper {

	public static LightEngine<?, SkyLightSectionStorage> getSkyLightProvider(LevelLightEngine provider) {
		return (LightEngine<?, SkyLightSectionStorage>) ((LevelLightEngineAccessor) provider).getSkyEngine();
	}

	public static LightEngine<? extends DataLayerStorageMap<?>, BlockLightSectionStorage> getBlockLightProvider(LevelLightEngine provider) {
		return (LightEngine<?, BlockLightSectionStorage>) ((LevelLightEngineAccessor) provider).getBlockEngine();
	}

	public static <S extends LayerLightSectionStorage<?>> S getLightStorage(LightEngine<?, S> provider) {
		return ((LightEngineAccessor<?, S>) provider).getStorage();
	}

	public static SkyLightSectionStorage getSkyLightStorage(LevelLightEngine provider) {
		return getLightStorage(getSkyLightProvider(provider));
	}

	public static BlockLightSectionStorage getBlockLightStorage(LevelLightEngine provider) {
		return getLightStorage(getBlockLightProvider(provider));
	}

}
