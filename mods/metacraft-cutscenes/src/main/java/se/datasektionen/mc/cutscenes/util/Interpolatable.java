package se.datasektionen.mc.cutscenes.util;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

public interface Interpolatable {

	DoubleList getValues(@Nullable ServerPlayerEntity player, @Nullable CutsceneInstance cutscene);

	default Int2BooleanMap getFieldsToIgnore(@Nullable ServerPlayerEntity player, @Nullable CutsceneInstance cutscene) {
		return Int2BooleanMaps.EMPTY_MAP;
	}

	default boolean isDynamic() {
		return false;
	}

}
