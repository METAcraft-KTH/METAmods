package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;

import java.io.IOException;
import java.io.StringWriter;

public class DataBlockRegistry {

	public static final Registry<DataBlockType<?>> REGISTRY = FabricRegistryBuilder.<DataBlockType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftDungeons.getID("data_block"))
	).buildAndRegister();

	public static final DataBlockType<EntranceDataBlock> ENTRANCE = register(
			"entrance", new DataBlockType<>(EntranceDataBlock.CODEC)
	);
	public static final DataBlockType<PortalDeeper> PORTAL_DEEPER = register(
			"portal_deeper", new DataBlockType<>(PortalDeeper.CODEC)
	);

	public static final DataBlockType<MusicPlayer> MUSIC_PLAYER = register(
			"music_player", new DataBlockType<>(MusicPlayer.CODEC)
	);

	public static final Codec<? extends DataBlock> PARSER_CODEC = Codec.STRING.flatXmap(
			line -> {
				int open = line.indexOf('{');
				if (open != -1) {
					return REGISTRY.getCodec().parse(JavaOps.INSTANCE, line.substring(0, open)).flatMap(
							type -> {
								int close = line.lastIndexOf('}');
								if (close != -1) {
									return type.codec.parse(
											JsonOps.INSTANCE, JsonParser.parseString(line.substring(open, close+1))
									);
								} else {
									return DataResult.error(() -> "No closing brace");
								}
							}
					);
				}
				return REGISTRY.getCodec().parse(JavaOps.INSTANCE, line).flatMap(
						type -> type.codec.parse(NbtOps.INSTANCE, new NbtCompound())
				);
			},
			data -> REGISTRY.getCodec().encodeStart(JavaOps.INSTANCE, data.getType()).flatMap(
					typeName -> {
						String prefix;
						if (typeName instanceof Identifier id && id.getNamespace().equals("minecraft")) {
							prefix = id.getPath();
						} else {
							prefix = typeName.toString();
						}
						return data.getType().getCodec().encodeStart(JsonOps.INSTANCE, data).flatMap(json -> {
							var string = new StringWriter();
							try {
								Streams.write(json, new JsonWriter(string));
								return DataResult.success(prefix + string);
							} catch (IOException e) {
								return DataResult.error(e::getLocalizedMessage);
							}
						});
					}
			)
	);

	private static <T extends DataBlockType<? extends DataBlock>> T register(String id, T object) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), object);
	}

	public record DataBlockType<T extends DataBlock>(Codec<T> codec) {
		public Codec<DataBlock> getCodec() {
			return (Codec<DataBlock>) codec;
		}
	}

	public static void init() {

	}

}
