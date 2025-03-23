package se.datasektionen.mc.metacraft_core.block.entities;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_core.block.blocks.MusicBlock;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.*;

public class MusicBlockEntity extends BlockEntity {
	public static final Codec<Pool<MusicEntry>> MUSIC_POOL_CODEC = Pool.createCodec(MusicEntry.CODEC);
	public static final String RANGE = "Range";
	public static final String BOX = "Box";
	public static final String MUSIC_CHOICES = "MusicChoices";
	public static final String CURRENT_MUSIC = "CurrentMusic";

	private static final String DEFAULT_MUSIC = "default";

	private MusicEntry currentEntry = null;
	private boolean firstTick = true;
	private String currentMusic = DEFAULT_MUSIC;
	private final Map<String, Pool<MusicEntry>> musicChoices = new HashMap<>();
	private double range = 128;
	private Box boundingBox;
	private Box cachedBox;
	private final Set<ServerPlayerEntity> trackedPlayers = new HashSet<>();

	public MusicBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public MusicBlockEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.MUSIC_PLAYER, pos, state);
	}

	private boolean shouldHearMusic(PlayerEntity player) {
		return player.squaredDistanceTo(pos.toCenterPos()) <= Math.pow(range, 2) || getBoundingBoxTransformed().contains(player.getPos());
	}

	private Box getBoundingBoxTransformed() {
		if (cachedBox != null) {
			return cachedBox;
		}
		if (boundingBox == null) {
			return Box.from(Vec3d.of(pos));
		}
		var mirror = getCachedState().get(MusicBlock.MIRROR);
		var rotation = getCachedState().get(MusicBlock.ROTATION);
		Vec3d pos1 = new Vec3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ).add(pos.toCenterPos());
		Vec3d pos2 = new Vec3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ).add(pos.toCenterPos());
		cachedBox = new Box(
				StructureTemplate.transformAround(pos1, mirror, rotation, pos),
				StructureTemplate.transformAround(pos2, mirror, rotation, pos)
		);
		return cachedBox;
	}

	public static void tick(World world, BlockPos pos, BlockState state, MusicBlockEntity musicPlayer) {
		if (world.isClient() || (!(world instanceof ServerWorld sw))) return;
		if (musicPlayer.firstTick) {
			musicPlayer.firstTick = false;
			musicPlayer.resetMusic();
		}
		if (musicPlayer.currentEntry == null) return;
		for (var player : sw.getPlayers()) {
			if (musicPlayer.shouldHearMusic(player)) {
				musicPlayer.trackedPlayers.add(player);
				MusicHelper.playMusic(player, musicPlayer.currentEntry, p -> {
					if (
							p.isDead() || !musicPlayer.shouldHearMusic(p) ||
									p.getWorld().getRegistryKey() != world.getRegistryKey() ||
									musicPlayer.isRemoved()
					) {
						musicPlayer.trackedPlayers.remove(p);
						return p.isDead() || !MusicHelper.isMusicPlaying(p, musicPlayer.currentEntry);
					}
					return true;
				});
			}
		}
	}

	public Optional<MusicEntry> getCurrentMusic(Random random) {
		if (currentMusic == null) return Optional.empty();
		return Optional.ofNullable(musicChoices.get(currentMusic)).flatMap(pool -> pool.getOrEmpty(random));
	}

	public void resetMusic() {
		trackedPlayers.forEach(MusicHelper::stopMusic);
		trackedPlayers.clear();
		if (world != null) {
			currentEntry = getCurrentMusic(world.getRandom()).orElse(null);
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
		markDirty();
	}

	public void setRange(double range) {
		this.range = range;
		boundingBox = null;
		markDirty();
	}

	public void setBoundingBox(Box box) {
		this.boundingBox = box;
		this.range = 0;
		markDirty();
	}

	public void setMusicTracks(Map<String, Pool<MusicEntry>> musicTracks) {
		this.musicChoices.clear();
		this.musicChoices.putAll(musicTracks);
		markDirty();
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
		super.readNbt(nbt, wrapper);

		this.musicChoices.clear();
		NbtCompound musicChoices = nbt.getCompoundOrEmpty(MUSIC_CHOICES);
		for (var key : musicChoices.getKeys()) {
			musicChoices.get(key, MUSIC_POOL_CODEC, wrapper.getOps(NbtOps.INSTANCE)).ifPresent(
					musicPool -> this.musicChoices.put(key, musicPool)
			);
		}

		if (nbt.contains(CURRENT_MUSIC)) {
			setMusicInternal(nbt.getString(CURRENT_MUSIC, DEFAULT_MUSIC));
		} else {
			setMusicInternal(null);
		}
		
		if (nbt.contains("Reset")) {
			resetMusic();
		}

		range = nbt.getDouble(RANGE, 128);
		if (nbt.contains(BOX)) {
			nbt.get(BOX, ExtraCodecs.BOX_CODEC).ifPresent(
					box -> {
						boundingBox = box;
						cachedBox = null;
					}
			);
		} else {
			boundingBox = null;
			cachedBox = null;
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
		super.writeNbt(nbt, wrapper);
		NbtCompound musicChoices = new NbtCompound();
		for (var entry : this.musicChoices.entrySet()) {
			musicChoices.put(entry.getKey(), MUSIC_POOL_CODEC, wrapper.getOps(NbtOps.INSTANCE), entry.getValue());
		}
		nbt.put(MUSIC_CHOICES, musicChoices);
		if (currentMusic != null) {
			nbt.putString(CURRENT_MUSIC, currentMusic);
		}
		nbt.putDouble(RANGE, range);
		if (boundingBox != null) {
			nbt.put(BOX, ExtraCodecs.BOX_CODEC, boundingBox);
		}
	}

}
