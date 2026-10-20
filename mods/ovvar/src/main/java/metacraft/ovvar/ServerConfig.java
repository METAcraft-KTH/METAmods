package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code server} block of {@code config/ovvar.json}: what this server calls itself. ovvar no
 * longer sets the MOTD from it (that is {@code server.properties}' job); the key stays so existing
 * files still read. Every key has a default.
 *
 * @param name this server's name, as players read it in the server list (default "METAcraft")
 */
public record ServerConfig(String name) {
	public static final ServerConfig DEFAULT = new ServerConfig("METAcraft");


	public static final MapCodec<ServerConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ConfigFields.field(Codec.STRING, "name", DEFAULT.name, "This server's name, as players read it in the server list.").forGetter(ServerConfig::name)
	).apply(instance, (name) -> new ServerConfig(name)));
}
