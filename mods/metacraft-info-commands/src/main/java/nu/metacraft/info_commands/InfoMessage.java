package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import nu.metacraft.lib.config.CommentCodec;

public record InfoMessage(String id, Component message) {
	public static final Codec<InfoMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			CommentCodec.comment(
				Codec.STRING.fieldOf("id"),
				"The unique id of this info message."
			).forGetter(InfoMessage::id),
			CommentCodec.comment(
				ComponentSerialization.CODEC.fieldOf("message"),
				"A raw text message that should be sent."
			).forGetter(InfoMessage::message)
	).apply(instance, InfoMessage::new));
}
