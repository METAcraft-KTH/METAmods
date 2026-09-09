package nu.metacraft.dungeons.dungeons.datablocks;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.blocks.MusicBlock;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.lib.util.METACodecs;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.phys.AABB;

public class MusicPlayer extends DataBlock {

	protected String selectedMusicTrack;
	protected Map<String, PlayerMusic> musicTracks;
	protected Either<Either<Double, AABB>, CalculatedArea> area;

	public static final MapCodec<MusicPlayer> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.STRING.fieldOf("selected_track").orElse("default").forGetter(player -> player.selectedMusicTrack),
			MusicBlockEntity.NAMED_MUSIC_POOLS_CODEC.fieldOf("tracks").forGetter(player -> player.musicTracks),
			Codec.either(
					Codec.either(Codec.DOUBLE, METACodecs.BOX_CODEC), CalculatedArea.CODEC
			).fieldOf("area").orElse(Either.right(CalculatedArea.DUNGEON)).forGetter(player -> player.area)
		).apply(instance, MusicPlayer::new)
	);

	public MusicPlayer(
			String selectedMusicTrack,
			Map<String, PlayerMusic> musicTracks,
			Either<Either<Double, AABB>, CalculatedArea> area
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
		var state = METAcraftBlocks.MUSIC_PLAYER.defaultBlockState();
		if (area.left().isPresent()) {
			state = state.setValue(MusicBlock.ROTATION, piece.getRotation())
					.setValue(MusicBlock.MIRROR, piece.getMirror());
		}
		parameters.dungeons.setBlockAndUpdate(pos, state);
		if (parameters.dungeons.getBlockEntity(pos) instanceof MusicBlockEntity musicPlayer) {
			area.ifLeft(area -> {
				area.ifLeft(musicPlayer::setRange);
				area.ifRight(musicPlayer::setBoundingBox);
			});
			area.ifRight(type -> {
				switch (type) {
					case ROOM -> {
						musicPlayer.setBoundingBox(
							AABB.of(piece.getBoundingBox()).move(Vec3.atCenterOf(pos).reverse())
						);
					}
					case DUNGEON -> {
						musicPlayer.setBoundingBox(
								AABB.of(parameters.structureBounds).move(Vec3.atCenterOf(pos).reverse())
						);
					}
				}
			});
			musicPlayer.setMusicTracks(musicTracks);
			musicPlayer.setMusic(selectedMusicTrack);
		}
	}

	public enum CalculatedArea implements StringRepresentable {
		DUNGEON("dungeon"),
		ROOM("room");

		public static final Codec<CalculatedArea> CODEC = StringRepresentable.fromEnum(CalculatedArea::values);

		private final String name;
		CalculatedArea(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
