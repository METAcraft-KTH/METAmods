package nu.metacraft.lib.config.comments;

import com.mojang.serialization.Codec;

public interface CodecExtension<T> {

	Codec<T> metacraft$comment(String comment);

}
