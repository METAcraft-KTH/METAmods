package nu.metacraft.cutscenes.registry;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.core.util.SerializableEntitySelector;

import java.util.List;
import java.util.stream.Stream;

public class ChunkAreaRegistry {

	public static final Registry<MapCodec<? extends ChunkArea>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends ChunkArea>>create(
			ResourceKey.createRegistryKey(Cutscenes.getID("chunk_area"))
	).buildAndRegister();

	public static final Codec<ChunkPos> CHUNK_OR_BLOCK_POS_CODEC = Codec.withAlternative(
			ChunkPos.CODEC, BlockPos.CODEC, ChunkPos::containing
	);

	protected static final Codec<ChunkArea> REGISTRY_CODEC = REGISTRY.byNameCodec().dispatch(
			ChunkArea::getCodec, c -> c
	);

	public static final Codec<ChunkArea> CODEC = Codec.withAlternative(
			REGISTRY_CODEC, ChunkPos.CODEC, SingleChunk::new
	);


	public static final MapCodec<ChunkRange> RANGE = register("range", ChunkRange.CODEC);
	public static final MapCodec<ChunksAround> AROUND = register("around", ChunksAround.CODEC);
	public static final MapCodec<SingleChunk> SINGLE = register("single", SingleChunk.CODEC);
	public static final MapCodec<Entities> ENTITIES = register("entities", Entities.CODEC);

	public static void init() {

	}

	private static <T extends ChunkArea> MapCodec<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), codec);
	}


	public interface ChunkArea {
		MapCodec<? extends ChunkArea> getCodec();
		Stream<ChunkPos> getPositions(ServerLevel world);
	}

	public record ChunkRange(ChunkPos lhs, ChunkPos rhs) implements ChunkArea {
		public static final MapCodec<ChunkRange> CODEC = CHUNK_OR_BLOCK_POS_CODEC.listOf().comapFlatMap(
				chunkList -> chunkList.size() == 2 ? DataResult.success(new ChunkRange(chunkList.getFirst(), chunkList.getLast())) : DataResult.error(() -> "Expected 2 chunk positions!"),
				chunks -> List.of(chunks.lhs, chunks.rhs)
		).fieldOf("range");

		@Override
		public Stream<ChunkPos> getPositions(ServerLevel world) {
			return ChunkPos.rangeClosed(lhs, rhs);
		}

		@Override
		public MapCodec<? extends ChunkArea> getCodec() {
			return RANGE;
		}
	}

	public record ChunksAround(ChunkPos center, int radius) implements ChunkArea {

		public static final MapCodec<ChunksAround> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						CHUNK_OR_BLOCK_POS_CODEC.fieldOf("center").forGetter(ChunksAround::center),
						Codec.INT.fieldOf("radius").forGetter(ChunksAround::radius)
				).apply(instance, ChunksAround::new)
		);

		@Override
		public Stream<ChunkPos> getPositions(ServerLevel world) {
			return ChunkPos.rangeClosed(center, radius);
		}

		@Override
		public MapCodec<? extends ChunkArea> getCodec() {
			return AROUND;
		}
	}

	public record SingleChunk(ChunkPos chunk) implements ChunkArea {

		public static final MapCodec<SingleChunk> CODEC = CHUNK_OR_BLOCK_POS_CODEC.xmap(
				SingleChunk::new, SingleChunk::chunk
		).fieldOf("chunk");

		@Override
		public Stream<ChunkPos> getPositions(ServerLevel world) {
			return Stream.of(chunk);
		}

		@Override
		public MapCodec<? extends ChunkArea> getCodec() {
			return SINGLE;
		}
	}

	public record Entities(SerializableEntitySelector selector) implements ChunkArea {

		public static final MapCodec<Entities> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						SerializableEntitySelector.CODEC.fieldOf("selector").forGetter(Entities::selector)
				).apply(instance, Entities::new)
		);

		@Override
		public Stream<ChunkPos> getPositions(ServerLevel world) {
			try {
				return selector.get().findEntities(
						world.getServer().getFunctions().getGameLoopSender()
				).stream().filter(e -> e.level() == world).map(Entity::chunkPosition).distinct();
			} catch (CommandSyntaxException e) {
				Cutscenes.LOGGER.error(e.getMessage());
				return Stream.of();
			}
		}

		@Override
		public MapCodec<? extends ChunkArea> getCodec() {
			return ENTITIES;
		}
	}
}
