package nu.metacraft.lib.config.comments;

import com.mojang.serialization.MapCodec;

public interface MapCodecExtension<T> {

	MapCodec<T> metacraft$comment(String comment);

}
