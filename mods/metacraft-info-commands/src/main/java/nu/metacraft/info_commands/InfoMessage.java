package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

public record InfoMessage(String id, Text message) {
	public static final Codec<InfoMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(InfoMessage::id),
		TextCodecs.CODEC.fieldOf("message").forGetter(InfoMessage::message)
	).apply(instance, InfoMessage::new));
}
