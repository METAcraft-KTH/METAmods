package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import nu.metacraft.lib.config.CommentCodec;

public record InfoConfig(
	Map<String, InfoNode> commands, boolean resendCommandTreeOnReload,
	boolean enableResendCommandTreeCommand, int infoMessageIntervalTicks,
	Component infoMessagePrefix, List<InfoMessage> infoMessages
) {

	public static final MapCodec<InfoConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			CommentCodec.comment(
				Codec.unboundedMap(Codec.STRING, InfoNode.CODEC),
				"All commands to add, see example commands for more info."
			).fieldOf("commands").forGetter(InfoConfig::commands),
			CommentCodec.comment(
				Codec.BOOL.fieldOf("resendCommandTreeOnReload"),
				"If true, all commands are reloaded when /reload is executed."
			).forGetter(InfoConfig::resendCommandTreeOnReload),
			CommentCodec.comment(
				Codec.BOOL.fieldOf("enableResendCommandTreeCommand"),
				"If true, a custom command is added to reload the commands."
			).forGetter(InfoConfig::enableResendCommandTreeCommand),
			CommentCodec.comment(
				Codec.INT.fieldOf("infoMessageIntervalTicks"),
				"The delay between each automatic info message in ticks."
			).forGetter(c -> c.infoMessageIntervalTicks),
			CommentCodec.comment(
				ComponentSerialization.CODEC,
				"The prefix for automatic info messages."
			).fieldOf("infoMessagePrefix").forGetter(c -> c.infoMessagePrefix),
			CommentCodec.comment(
				Codec.list(InfoMessage.CODEC).fieldOf("infoMessages"),
				"The list of all info messages."
			).forGetter(c -> c.infoMessages)
	).apply(instance, InfoConfig::new));

	public InfoConfig() {
		this(
				new HashMap<>(),
				true,
				false,
				15 * 60 * 20,
				Component.literal(" \uD83D\uDEC8 ").withStyle(style -> style.withColor(ChatFormatting.AQUA)),
				new ArrayList<>()
		);
	}


}
