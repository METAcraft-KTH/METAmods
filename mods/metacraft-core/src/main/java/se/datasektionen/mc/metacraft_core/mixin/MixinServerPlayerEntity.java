package se.datasektionen.mc.metacraft_core.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_core.entity.entities.PlayerMusicPoint;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public ServerPlayNetworkHandler networkHandler;

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow public abstract void sendMessage(Text message, boolean overlay);

	@Shadow @Final public MinecraftServer server;
	@Unique
	private static final String ARE_BLOCKS_MOVABLE = "AreBlocksMovable";

	@Unique
	private static final String CAMPUS_LODESTONE_BACK = "CampusLodestoneBack";

	@Unique
	private boolean movable = false;

	@Unique
	private final Map<RegistryEntry<SoundEvent>, MutableInt> potentiallyPlayingMusic = new HashMap<>();

	@Unique
	private int musicDelay;

	@Unique
	private MusicEntry music;
	@Unique
	private Predicate<ServerPlayerEntity> shouldContinuePlayingMusic = player -> true;

	@Unique
	private int displayTimer = 0;

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Unique
	private int musicStopTimer = -1;

	@Unique
	private PlayerMusicPoint musicPoint;

	@Unique
	@Nullable
	private RegistryKey<World> campusLodestoneBackWorld;

	@Unique
	@Nullable
	private BlockPos campusLodestoneBackPos;

	@Unique
	private void refreshMusicPoint(boolean shouldReset) {
		if (musicPoint == null || musicPoint.isRemoved()) {
			musicPoint = METAcraftEntities.POINT.create(getWorld());
			musicPoint.updatePositionAndAngles(getX(), getY(), getZ(), 0, 0);
			getWorld().spawnEntity(musicPoint);
			musicPoint.setPlayer((ServerPlayerEntity) (Object) this);
			musicPoint.sendToClient();
			if (shouldReset) {
				metacraft_lib$resetMusicTimer();
			}
		}
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		if (musicPoint != null) {
			musicPoint.discard();
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		((EntityExtensions) this).metacraft_lib$getBossBar().ifPresent(bar -> {
			if (!bar.getPlayers().contains((ServerPlayerEntity) (Object) this)) {
				bar.addPlayer((ServerPlayerEntity) (Object) this);
			}
		});

		if (musicPoint != null) {
			musicPoint.setPos(getX(), getY(), getZ());
			if (musicPoint.getWorld() != getWorld()) {
				musicPoint.teleport(
						getServerWorld(), getX(), getY(), getZ(), Set.of(), 0, 0
				);
			}
		}

		if (music != null) {
			refreshMusicPoint(true);
		} else if (musicPoint != null && musicPoint.isRemoved()) {
			musicPoint = null;
		}

		getServerWorld().getBiome(this.getBlockPos()).value().getMusic().ifPresent(music -> {
			if (potentiallyPlayingMusic.containsKey(music.getSound())) {
				potentiallyPlayingMusic.get(music.getSound()).setValue(music.getMinDelay());
			} else {
				potentiallyPlayingMusic.put(music.getSound(), new MutableInt(music.getMinDelay()));
			}
		});
		potentiallyPlayingMusic.keySet().removeIf(music -> {
			return potentiallyPlayingMusic.get(music).decrementAndGet() <= 0;
		});

		if (!shouldContinuePlayingMusic.test((ServerPlayerEntity) (Object) this)) {
			if (musicStopTimer < 0) {
				musicStopTimer = 10;
			} else if (musicStopTimer > 0) {
				musicStopTimer--;
			} else {
				metacraft_lib$setMusicEntry(null);
				musicStopTimer = -1;
			}
		} else {
			musicStopTimer = -1;
		}

		if (displayTimer > 0) {
			if (music != null) {
				music.credit().ifPresent(credit -> {
					this.sendMessage(credit.getPlayingCredit(), true);
				});
				displayTimer--;
			} else {
				displayTimer = 0;
			}
		}

		if (musicDelay > 0) {
			musicDelay--;
		} else {
			if (music != null) {
				playMusic();
			}
		}

		if (music != null) {
			disableVanillaMusic();
		}
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.music = ((MixinServerPlayerEntity) (Object) oldPlayer).music;
		this.musicDelay = ((MixinServerPlayerEntity) (Object) oldPlayer).musicDelay;
		this.displayTimer = ((MixinServerPlayerEntity) (Object) oldPlayer).displayTimer;
		this.shouldContinuePlayingMusic = ((MixinServerPlayerEntity) (Object) oldPlayer).shouldContinuePlayingMusic;
		this.musicStopTimer = ((MixinServerPlayerEntity) (Object) oldPlayer).musicStopTimer;
		this.musicPoint = ((MixinServerPlayerEntity) (Object) oldPlayer).musicPoint;
		if (musicPoint != null) {
			musicPoint.setPlayer((ServerPlayerEntity) (Object) this);
		}
		this.campusLodestoneBackWorld = ((MixinServerPlayerEntity) (Object) oldPlayer).campusLodestoneBackWorld;
		this.campusLodestoneBackPos = ((MixinServerPlayerEntity) (Object) oldPlayer).campusLodestoneBackPos;
		if (!server.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
				var stack = oldPlayer.getInventory().getStack(i);
				if (stack.contains(METAcraftComponents.SOULBOUND)) {
					this.getInventory().setStack(i, stack);
				}
			}
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(ARE_BLOCKS_MOVABLE, movable);
		if (this.campusLodestoneBackWorld != null && this.campusLodestoneBackPos != null) {
			NbtCompound backData = new NbtCompound();
			backData.putString("world", this.campusLodestoneBackWorld.getValue().toString());
			backData.putInt("x", this.campusLodestoneBackPos.getX());
			backData.putInt("y", this.campusLodestoneBackPos.getY());
			backData.putInt("z", this.campusLodestoneBackPos.getZ());
			nbt.put(CAMPUS_LODESTONE_BACK, backData);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
	public void readNbt(NbtCompound nbt, CallbackInfo ci) {
		movable = nbt.getBoolean(ARE_BLOCKS_MOVABLE);
		if (nbt.contains(CAMPUS_LODESTONE_BACK, NbtElement.COMPOUND_TYPE)) {
			NbtCompound backData = nbt.getCompound(CAMPUS_LODESTONE_BACK);
			Identifier worldKey = Identifier.tryParse(backData.getString("world"));
			if (worldKey != null) {
				this.campusLodestoneBackWorld = RegistryKey.of(RegistryKeys.WORLD, worldKey);
			}
			this.campusLodestoneBackPos = new BlockPos(
				backData.getInt("x"),
				backData.getInt("y"),
				backData.getInt("z")
			);
		}
	}

	@Unique
	private void disableVanillaMusic() {
		this.networkHandler.sendPacket(new StopSoundS2CPacket(
				SoundEvents.MUSIC_GAME.value().getId(), SoundCategory.MUSIC
		));
		this.networkHandler.sendPacket(new StopSoundS2CPacket(
				SoundEvents.MUSIC_UNDER_WATER.value().getId(), SoundCategory.MUSIC
		));
		if (this.isCreative()) {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					SoundEvents.MUSIC_CREATIVE.value().getId(), SoundCategory.MUSIC
			));
		}
		if (this.getServerWorld().getRegistryKey() == World.END) {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					SoundEvents.MUSIC_END.value().getId(), SoundCategory.MUSIC
			));
		}
		potentiallyPlayingMusic.keySet().forEach(music -> {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					music.value().getId(), SoundCategory.MUSIC
			));
		});
		potentiallyPlayingMusic.clear();
	}

	@Unique
	private void playMusic() {
		if (this.music != null) {
			refreshMusicPoint(false);
			this.networkHandler.sendPacket(new StopSoundS2CPacket(null, SoundCategory.MUSIC));
			this.musicDelay = music.length();
			this.networkHandler.sendPacket(
				new PlaySoundFromEntityS2CPacket(
					music.music(), SoundCategory.MUSIC, musicPoint, 1, music.pitch(), this.getRandom().nextLong()
				)
			);
		}
	}

	@Override
	public boolean metacraft_lib$setMusicEntry(MusicEntry entry, boolean skipPriorityCheck) {
		boolean continuePrevious = Objects.equals(this.music, entry);
		if (entry != null && this.music != null && (this.music.priority() >= entry.priority() && !skipPriorityCheck) && !continuePrevious) {
			return false;
		}
		shouldContinuePlayingMusic = player -> true;
		if (this.music != null && !continuePrevious) {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(this.music.music().value().getId(), SoundCategory.MUSIC));
			if (musicPoint != null && entry == null) {
				musicPoint.discard();
			}
		}
		this.music = entry;
		if (!continuePrevious) {
			displayTimer = 100;
			playMusic();
		}
		return true;
	}

	@Override
	public void metacraft_lib$setContinuePlayingMusicPredicate(Predicate<ServerPlayerEntity> predicate) {
		this.shouldContinuePlayingMusic = predicate;
	}

	@Override
	public boolean metacraft_lib$hasMusicEntry(MusicEntry entry, boolean allowEquivalent) {
		if (allowEquivalent) {
			return Objects.equals(entry, music);
		} else {
			return music == entry;
		}
	}

	@Override
	public void metacraft_lib$resetMusicTimer() {
		if (this.music != null) {
			playMusic();
		}
	}

	@Override
	public PlayerMusicPoint metacraft_lib$getMusicPoint() {
		return musicPoint;
	}

	@Override
	public void metacraft_core$setBlocksPistonMovable(boolean movable) {
		this.movable = movable;
	}

	@Override
	public boolean metacraft_core$areBlocksPistonMovable() {
		return movable;
	}

	@Override
	@Nullable
	public RegistryKey<World> metacraft_core$getCampusLodestoneBackWorld() {
		return this.campusLodestoneBackWorld;
	}

	@Override
	@Nullable
	public BlockPos metacraft_core$getCampusLodestoneBackPos() {
		return this.campusLodestoneBackPos;
	}

	@Override
	public void metacraft_core$setCampusLodestoneBackPos(RegistryKey<World> world, BlockPos pos) {
		this.campusLodestoneBackWorld = world;
		this.campusLodestoneBackPos = pos;
	}

	@Override
	public void metacraft_core$unsetCampusLodestoneBackPos() {
		this.campusLodestoneBackWorld = null;
		this.campusLodestoneBackPos = null;
	}
}
