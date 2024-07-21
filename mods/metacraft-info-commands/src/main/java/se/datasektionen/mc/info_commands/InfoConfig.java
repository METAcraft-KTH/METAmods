package se.datasektionen.mc.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

public class InfoConfig {

	public static final Codec<InfoConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, InfoNode.CODEC).fieldOf("commands").forGetter(InfoConfig::getCommands),
			Codec.BOOL.fieldOf("resendCommandTreeOnReload").forGetter(InfoConfig::resendCommandTreeOnReload),
			Codec.BOOL.fieldOf("enableResendCommandTreeCommand").forGetter(InfoConfig::enableResendCommandTreeCommand)
	).apply(instance, InfoConfig::new));

	private final Map<String, InfoNode> commands;
	private final boolean resendCommandTreeOnReload;
	private final boolean enableResendCommandTreeCommand;

	public InfoConfig(Map<String, InfoNode> commands, boolean resendCommandTreeOnReload, boolean enableResendCommandTreeCommand) {
		this.commands = commands;
		this.resendCommandTreeOnReload = resendCommandTreeOnReload;
		this.enableResendCommandTreeCommand = enableResendCommandTreeCommand;
	}

	public InfoConfig() {
		this(new HashMap<>(), true, false);
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
}
