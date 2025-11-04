package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public record InfoMessage(String id, Component message) {
	public static final Codec<InfoMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(InfoMessage::id),
		ComponentSerialization.CODEC.fieldOf("message").forGetter(InfoMessage::message)
	).apply(instance, InfoMessage::new));
}
