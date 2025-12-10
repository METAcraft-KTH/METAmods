package nu.metacraft.core.block.entities;

import com.mojang.serialization.Codec;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.core.block.blocks.MusicBlock;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.core.util.helper.MusicHelper;
import nu.metacraft.lib.util.METACodecs;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MusicBlockEntity extends BlockEntity {
	public static final Codec<Map<String, PlayerMusic>> NAMED_MUSIC_POOLS_CODEC = Codec.unboundedMap(Codec.STRING, PlayerMusic.EASY_CODEC);
	public static final String RANGE = "Range";
	public static final String BOX = "Box";
	public static final String MUSIC_CHOICES = "MusicChoices";
	public static final String CURRENT_MUSIC = "CurrentMusic";

	private static final String DEFAULT_MUSIC = "default";

	private PlayerMusic currentEntry = null;
	private boolean firstTick = true;
	private String currentMusic = DEFAULT_MUSIC;
	private final Map<String, PlayerMusic> musicChoices = new HashMap<>();
	private double range = 128;
	private AABB boundingBox;
	private AABB cachedBox;
	private final Set<ServerPlayer> trackedPlayers = new HashSet<>();

	public MusicBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public MusicBlockEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.MUSIC_PLAYER, pos, state);
	}

	private boolean shouldHearMusic(Player player) {
		return player.distanceToSqr(worldPosition.getCenter()) <= Math.pow(range, 2) || getBoundingBoxTransformed().contains(player.position());
	}

	private AABB getBoundingBoxTransformed() {
		if (cachedBox != null) {
			return cachedBox;
		}
		if (boundingBox == null) {
			return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(worldPosition));
		}
		var mirror = getBlockState().getValue(MusicBlock.MIRROR);
		var rotation = getBlockState().getValue(MusicBlock.ROTATION);
		Vec3 pos1 = new Vec3(boundingBox.minX, boundingBox.minY, boundingBox.minZ).add(worldPosition.getCenter());
		Vec3 pos2 = new Vec3(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ).add(worldPosition.getCenter());
		cachedBox = new AABB(
				StructureTemplate.transform(pos1, mirror, rotation, worldPosition),
				StructureTemplate.transform(pos2, mirror, rotation, worldPosition)
		);
		return cachedBox;
	}

	public static void tick(Level world, BlockPos pos, BlockState state, MusicBlockEntity musicPlayer) {
		if (world.isClientSide() || (!(world instanceof ServerLevel sw))) return;
		if (musicPlayer.firstTick) {
			musicPlayer.firstTick = false;
			musicPlayer.resetMusic();
		}
		if (musicPlayer.currentEntry == null) return;
		for (var player : sw.players()) {
			if (musicPlayer.shouldHearMusic(player)) {
				musicPlayer.trackedPlayers.add(player);
				MusicHelper.playMusic(player, musicPlayer.currentEntry, p -> {
					if (
							p.isDeadOrDying() || !musicPlayer.shouldHearMusic(p) ||
									p.level().dimension() != world.dimension() ||
									musicPlayer.isRemoved()
					) {
						musicPlayer.trackedPlayers.remove(p);
						return p.isDeadOrDying() || !MusicHelper.isMusicPlaying(p, musicPlayer.currentEntry);
					}
					return true;
				});
			}
		}
	}

	public Optional<PlayerMusic> getCurrentMusic() {
		if (currentMusic == null) return Optional.empty();
		return Optional.ofNullable(musicChoices.get(currentMusic));
	}

	public void resetMusic() {
		trackedPlayers.forEach(MusicHelper::stopMusic);
		trackedPlayers.clear();
		if (level != null) {
			currentEntry = getCurrentMusic().orElse(null);
		}
	}

	private void setMusicInternal(String name) {
		if (name != null && !musicChoices.containsKey(name)) return;
		if (!Objects.equals(currentMusic, name)) {
			currentMusic = name;
			resetMusic();
		}
	}

	public void setMusic(String name) {
		setMusicInternal(name);
		setChanged();
	}

	public void setRange(double range) {
		this.range = range;
		boundingBox = null;
		setChanged();
	}

	public void setBoundingBox(AABB box) {
		this.boundingBox = box;
		this.range = 0;
		setChanged();
	}

	public void setMusicTracks(Map<String, PlayerMusic> musicTracks) {
		this.musicChoices.clear();
		this.musicChoices.putAll(musicTracks);
		setChanged();
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);

		this.musicChoices.clear();
		nbt.read(MUSIC_CHOICES, NAMED_MUSIC_POOLS_CODEC).ifPresent(
				this.musicChoices::putAll
		);

		setMusicInternal(nbt.getStringOr(CURRENT_MUSIC, null));

		if (nbt.read("Reset", net.minecraft.util.ExtraCodecs.NBT).isPresent()) {
			resetMusic();
		}

		range = nbt.getDoubleOr(RANGE, 128);
		nbt.read(BOX, METACodecs.BOX_CODEC).ifPresentOrElse(
				box -> {
					boundingBox = box;
					cachedBox = null;
				},
				() -> {
					boundingBox = null;
					cachedBox = null;
				}
		);
	}

	@Override
	public void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);

		nbt.store(MUSIC_CHOICES, NAMED_MUSIC_POOLS_CODEC, musicChoices);
		if (currentMusic != null) {
			nbt.putString(CURRENT_MUSIC, currentMusic);
		}
		nbt.putDouble(RANGE, range);
		if (boundingBox != null) {
			nbt.store(BOX, METACodecs.BOX_CODEC, boundingBox);
		}
	}

}
