package nu.metacraft.lib.config.comments.ops;

import com.mojang.serialization.RecordBuilder;

import java.util.Map;

public interface RecordBuilderWithComments<T> extends RecordBuilder<T> {

	RecordBuilderWithComments<T> metacraft$addComments(Map<T, String> comments);

}
