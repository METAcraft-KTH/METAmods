package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfoConfig {

	public static final Codec<InfoConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, InfoNode.CODEC).fieldOf("commands").forGetter(InfoConfig::getCommands),
			Codec.BOOL.fieldOf("resendCommandTreeOnReload").forGetter(InfoConfig::resendCommandTreeOnReload),
			Codec.BOOL.fieldOf("enableResendCommandTreeCommand").forGetter(InfoConfig::enableResendCommandTreeCommand),
			Codec.INT.fieldOf("infoMessageIntervalTicks").forGetter(c -> c.infoMessageIntervalTicks),
			TextCodecs.CODEC.fieldOf("infoMessagePrefix").forGetter(c -> c.infoMessagePrefix),
			Codec.list(InfoMessage.CODEC).fieldOf("infoMessages").forGetter(c -> c.infoMessages)
	).apply(instance, InfoConfig::new));

	private final Map<String, InfoNode> commands;
	private final boolean resendCommandTreeOnReload;
	private final boolean enableResendCommandTreeCommand;
	private final int infoMessageIntervalTicks;
	private final Text infoMessagePrefix;
	private final List<InfoMessage> infoMessages;

	public InfoConfig(Map<String, InfoNode> commands, boolean resendCommandTreeOnReload, boolean enableResendCommandTreeCommand, int infoMessageIntervalTicks, Text infoMessagePrefix, List<InfoMessage> infoMessages) {
		this.commands = commands;
		this.resendCommandTreeOnReload = resendCommandTreeOnReload;
		this.enableResendCommandTreeCommand = enableResendCommandTreeCommand;
        this.infoMessageIntervalTicks = infoMessageIntervalTicks;
        this.infoMessagePrefix = infoMessagePrefix;
        this.infoMessages = infoMessages;
    }

	public InfoConfig() {
		this(
			new HashMap<>(),
			true,
			false,
			15 * 60 * 20,
			Text.literal(" \uD83D\uDEC8 ").styled(style -> style.withColor(Formatting.AQUA)),
			new ArrayList<>()
		);
	}

	public Map<String, InfoNode> getCommands() {
		return commands;
	}

	public boolean resendCommandTreeOnReload() {
		return resendCommandTreeOnReload;
	}

	public boolean enableResendCommandTreeCommand() {
		return enableResendCommandTreeCommand;
	}

	public int getInfoMessageIntervalTicks() {
		return this.infoMessageIntervalTicks;
	}

	public Text getInfoMessagePrefix() {
		return this.infoMessagePrefix;
	}

	public List<InfoMessage> getInfoMessages() {
		return this.infoMessages;
	}
}
