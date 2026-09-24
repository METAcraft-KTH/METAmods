package nu.metacraft.lib.config.comments.ops;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.Map;

public interface OpsWithComments<T> extends DynamicOps<T> {

	DataResult<T> metacraft$withComments(T value, Map<T, String> comments);

	DataResult<T> metacraft$withComments(T value, Int2ObjectMap<String> comments);

}
