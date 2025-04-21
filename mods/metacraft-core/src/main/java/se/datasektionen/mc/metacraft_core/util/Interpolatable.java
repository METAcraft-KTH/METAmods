package se.datasektionen.mc.metacraft_core.util;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;

public interface Interpolatable<C> {

	DoubleList getValues(C context);

	default Int2BooleanMap getFieldsToIgnore(C context) {
		return Int2BooleanMaps.EMPTY_MAP;
	}

	default boolean isDynamic() {
		return false;
	}

}
