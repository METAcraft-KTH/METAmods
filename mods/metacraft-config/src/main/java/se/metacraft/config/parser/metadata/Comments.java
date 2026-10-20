package se.metacraft.config.parser.metadata;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.minecraft.network.chat.Component;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

import java.util.Map;
import java.util.stream.Collectors;

public record Comments(Map<Object, Component> comments) implements Metadata {
	@Override
	public MetadataKey<Comments> key() {
		return MetadataKey.COMMENTS;
	}


	public <R> Map<R, Component> getMapFor(DynamicOps<R> ops) {
		return comments.entrySet().stream().collect(
			Collectors.toMap(
				k -> JavaOps.INSTANCE.convertTo(ops, k.getKey()),
				Map.Entry::getValue
			)
		);
	}
}
