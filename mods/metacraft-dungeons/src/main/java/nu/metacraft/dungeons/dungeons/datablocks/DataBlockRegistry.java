package nu.metacraft.dungeons.dungeons.datablocks;

import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import nu.metacraft.dungeons.METAcraftDungeons;

import java.io.IOException;
import java.io.StringWriter;

public class DataBlockRegistry {

	public static final Registry<DataBlockType<?>> REGISTRY = FabricRegistryBuilder.<DataBlockType<?>>create(
			ResourceKey.createRegistryKey(METAcraftDungeons.getID("data_block"))
	).buildAndRegister();

	public static final DataBlockType<EntranceDataBlock> ENTRANCE = register(
			"entrance", new DataBlockType<>(EntranceDataBlock.CODEC)
	);
	public static final DataBlockType<PortalDeeper> PORTAL_DEEPER = register(
			"portal_deeper", new DataBlockType<>(PortalDeeper.CODEC)
	);

	public static final DataBlockType<PortalWithDestination> PORTAL = register(
			"portal", new DataBlockType<>(PortalWithDestination.CODEC)
	);

	public static final DataBlockType<BlockDataBlock> BLOCK = register(
			"block", new DataBlockType<>(BlockDataBlock.CODEC)
	);

	public static final DataBlockType<MusicPlayer> MUSIC_PLAYER = register(
			"music_player", new DataBlockType<>(MusicPlayer.CODEC)
	);

	public static final Codec<DataBlock> CODEC = REGISTRY.byNameCodec().dispatch(DataBlock::getType, DataBlockType::codec);

	public static final Codec<? extends DataBlock> PARSER_CODEC = new Codec<DataBlock>() {
		@Override
		public <T> DataResult<Pair<DataBlock, T>> decode(DynamicOps<T> ops, T input) {
			if (ops instanceof RegistryOps<T> registryOps) {
				return Codec.STRING.decode(ops, input).flatMap(
						data -> {
							var line = data.getFirst();
							int open = line.indexOf('{');
							if (open != -1) {
								return REGISTRY.byNameCodec().parse(registryOps.withParent(JavaOps.INSTANCE), line.substring(0, open)).flatMap(
										type -> {
											int close = line.lastIndexOf('}');
											if (close != -1) {
												return type.getCodec().parse(
														registryOps.withParent(JsonOps.INSTANCE), JsonParser.parseString(line.substring(open, close+1))
												).map(
														res -> Pair.of(res, data.getSecond())
												);
											} else {
												return DataResult.error(() -> "No closing brace");
											}
										}
								);
							}
							return REGISTRY.byNameCodec().parse(registryOps.withParent(JavaOps.INSTANCE), line).flatMap(
									type -> type.getCodec().parse(
											registryOps.withParent(NbtOps.INSTANCE), new CompoundTag()
									)
							).map(res -> Pair.of(res, data.getSecond()));
						}
				);
			} else {
				return DataResult.error(() -> "Could not access registries, this is a bug!");
			}
		}

		@Override
		public <T> DataResult<T> encode(DataBlock data, DynamicOps<T> ops, T init) {
			if (ops instanceof RegistryOps<T> registryOps) {
				return REGISTRY.byNameCodec().encodeStart(registryOps.withParent(JavaOps.INSTANCE), data.getType()).flatMap(
						typeName -> {
							String prefix;
							if (typeName instanceof Identifier id && id.getNamespace().equals("minecraft")) {
								prefix = id.getPath();
							} else {
								prefix = typeName.toString();
							}
							return data.getType().getCodec().encodeStart(registryOps.withParent(JsonOps.INSTANCE), data).flatMap(json -> {
								var string = new StringWriter();
								try {
									Streams.write(json, new JsonWriter(string));
									return DataResult.success(prefix + string);
								} catch (IOException e) {
									return DataResult.error(e::getLocalizedMessage);
								}
							});
						}
				).flatMap(
						line -> Codec.STRING.encode(line, ops, init)
				);
			} else {
				return DataResult.error(() -> "Could not access registries, this is a bug!");
			}
		}
	};

	private static <T extends DataBlockType<? extends DataBlock>> T register(String id, T object) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), object);
	}

	public record DataBlockType<T extends DataBlock>(MapCodec<T> codec) {
		public Codec<DataBlock> getCodec() {
			return (Codec<DataBlock>) codec.codec();
		}
	}

	public static void init() {

	}

}
