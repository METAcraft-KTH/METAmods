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

public record InfoConfig(
	Map<String, InfoNode> commands, boolean resendCommandTreeOnReload,
	boolean enableResendCommandTreeCommand, int infoMessageIntervalTicks,
	Component infoMessagePrefix, List<InfoMessage> infoMessages
) {

	public static final MapCodec<InfoConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, InfoNode.CODEC).fieldOf("commands").forGetter(InfoConfig::commands),
			Codec.BOOL.fieldOf("resendCommandTreeOnReload").forGetter(InfoConfig::resendCommandTreeOnReload),
			Codec.BOOL.fieldOf("enableResendCommandTreeCommand").forGetter(InfoConfig::enableResendCommandTreeCommand),
			Codec.INT.fieldOf("infoMessageIntervalTicks").forGetter(c -> c.infoMessageIntervalTicks),
			ComponentSerialization.CODEC.fieldOf("infoMessagePrefix").forGetter(c -> c.infoMessagePrefix),
			Codec.list(InfoMessage.CODEC).fieldOf("infoMessages").forGetter(c -> c.infoMessages)
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
