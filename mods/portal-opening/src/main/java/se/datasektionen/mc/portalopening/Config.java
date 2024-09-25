package se.datasektionen.mc.portalopening;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.portalopening.raid.Wave;

import java.util.ArrayList;
import java.util.List;

public class Config {

	public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.list(Wave.wave).fieldOf("waves").forGetter(Config::getWaves),
			Codec.STRING.fieldOf("commandOnRaidEnd").forGetter(config -> config.commandOnRaidEnd)
	).apply(instance, Config::new));

	private final List<Wave> waves;
	private String commandOnRaidEnd;

	public Config(List<Wave> waves, String commandOnRaidEnd) {
		this.waves = waves;
		this.commandOnRaidEnd = commandOnRaidEnd;
	}

	public Config() {
		this(
			new ArrayList<>(),
			"tellraw @a {\"text\":\"Raid over!\"}"
		);
	}

	public List<Wave> getWaves() {
		return waves;
	}

	public String getCommandOnRaidEnd() {
		return commandOnRaidEnd;
	}

}
