package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code server} block of {@code config/ovvar.json}: what this server calls itself. It is the
 * first half of the MOTD ({@link Motd}); the second half is what sewing does here, which is
 * {@code stash.minigame_server}. Every key has a default.
 *
 * @param name this server's name, as players read it in the server list (default "METAcraft")
 */
public record ServerConfig(String name) {
	public static final ServerConfig DEFAULT = new ServerConfig("METAcraft");

	/** Written into the file as {@code _help}, since JSON has no comments. */
	public static final Map<String, String> HELP = new LinkedHashMap<>();
	static {
		HELP.put("_about", "What this server calls itself. It is the first half of the MOTD; the second half is stash.minigame_server.");
		HELP.put("name", "This server's name, as players read it in the server list.");
	}

	public static final MapCodec<ServerConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("_help", java.util.Map.of()).forGetter(c -> HELP),
			Codec.STRING.optionalFieldOf("name", DEFAULT.name).forGetter(ServerConfig::name)
	).apply(instance, (help, name) -> new ServerConfig(name)));
}
