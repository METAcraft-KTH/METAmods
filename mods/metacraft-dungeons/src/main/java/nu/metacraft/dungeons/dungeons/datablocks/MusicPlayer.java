package nu.metacraft.dungeons.dungeons.datablocks;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.blocks.MusicBlock;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.Map;

public class MusicPlayer extends DataBlock {

	protected String selectedMusicTrack;
	protected Map<String, Pool<MusicEntry>> musicTracks;
	protected Either<Either<Double, Box>, CalculatedArea> area;

	public static final MapCodec<MusicPlayer> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.STRING.fieldOf("selected_track").orElse("default").forGetter(player -> player.selectedMusicTrack),
			Codec.unboundedMap(
					Codec.STRING, MusicBlockEntity.MUSIC_POOL_CODEC
			).fieldOf("tracks").forGetter(player -> player.musicTracks),
			Codec.either(
					Codec.either(Codec.DOUBLE, ExtraCodecs.BOX_CODEC), CalculatedArea.CODEC
			).fieldOf("area").orElse(Either.right(CalculatedArea.DUNGEON)).forGetter(player -> player.area)
		).apply(instance, MusicPlayer::new)
	);

	public MusicPlayer(
			String selectedMusicTrack,
			Map<String, Pool<MusicEntry>> musicTracks,
			Either<Either<Double, Box>, CalculatedArea> area
	) {
		this.selectedMusicTrack = selectedMusicTrack;
		this.musicTracks = musicTracks;
		this.area = area;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.MUSIC_PLAYER;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		var state = METAcraftBlocks.MUSIC_PLAYER.getDefaultState();
		if (area.left().isPresent()) {
			state = state.with(MusicBlock.ROTATION, piece.getRotation())
					.with(MusicBlock.MIRROR, piece.getMirror());
		}
		parameters.dungeons.setBlockState(pos, state);
		if (parameters.dungeons.getBlockEntity(pos) instanceof MusicBlockEntity musicPlayer) {
			area.ifLeft(area -> {
				area.ifLeft(musicPlayer::setRange);
				area.ifRight(musicPlayer::setBoundingBox);
			});
			area.ifRight(type -> {
				switch (type) {
					case ROOM -> {
						musicPlayer.setBoundingBox(
							Box.from(piece.getBoundingBox()).offset(pos.toCenterPos().negate())
						);
					}
					case DUNGEON -> {
						musicPlayer.setBoundingBox(
								Box.from(parameters.structureBounds).offset(pos.toCenterPos().negate())
						);
					}
				}
			});
			musicPlayer.setMusicTracks(musicTracks);
			musicPlayer.setMusic(selectedMusicTrack);
		}
	}

	public enum CalculatedArea implements StringIdentifiable {
		DUNGEON("dungeon"),
		ROOM("room");

		public static final Codec<CalculatedArea> CODEC = StringIdentifiable.createCodec(CalculatedArea::values);

		private final String name;
		private CalculatedArea(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}
}
