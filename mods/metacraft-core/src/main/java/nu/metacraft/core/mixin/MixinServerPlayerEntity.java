package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.core.music.PlayerMusic;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.core.music.MusicTimerTracker;
import nu.metacraft.core.preferences.PreferenceData;
import nu.metacraft.core.util.METAcraftCoreData;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public ServerPlayNetworkHandler networkHandler;

	public MixinServerPlayerEntity(World world, GameProfile profile) {
		super(world, profile);
	}

	@Shadow public abstract void sendMessage(Text message, boolean overlay);

	@Shadow @Final
	private MinecraftServer server;

	@Shadow public abstract ServerWorld getEntityWorld();

	@Unique
	private static final String ARE_BLOCKS_MOVABLE = "AreBlocksMovable";

	@Unique
	private static final String PREFERENCES = "metacraft:preferences";


	@Unique
	private final PreferenceData preferenceData = new PreferenceData();

	@Unique
	private boolean movable = false;

	@Unique
	private final Map<RegistryEntry<SoundEvent>, MutableInt> potentiallyPlayingMusic = new HashMap<>();

	@Unique
	private long musicStartTime;

	@Unique
	private int musicLengthMillis;

	@Unique
	private boolean inIntro;

	@Unique
	private volatile PlayerMusic music;

	@Unique
	private volatile MusicEntry currentEntry;

	@Unique
	private final Set<MusicEntry> seenCreditFor = new HashSet<>();

	@Unique
	private final Queue<PlayerMusic> musicEntryQueue = new PriorityQueue<>();

	@Unique
	private final Map<PlayerMusic, Predicate<ServerPlayerEntity>> musicEntriesInQueue = new HashMap<>();


	@Unique
	private Predicate<ServerPlayerEntity> shouldContinuePlayingMusic = player -> true;

	@Unique
	private boolean skipQueue;

	@Unique
	private int displayTimer = 0;

	@Unique
	private int musicStopTimer = -1;

	@Unique
	private ElementHolder pointHolder;

	@Unique
	private GenericEntityElement point;

	@Unique
	private ManualAttachment pointAttachment;

	@Unique
	private void refreshMusicPoint(boolean shouldReset) {
		if (pointHolder == null) {
			pointHolder = new ElementHolder();
			point = new GenericEntityElement() {
				@Override
				protected EntityType<? extends Entity> getEntityType() {
					return EntityType.MARKER;
				}
			};
			pointHolder.addElement(point);
			pointHolder.startWatching((ServerPlayerEntity) (Object) this);
			pointAttachment = new ManualAttachment(pointHolder, getEntityWorld(), this::getEntityPos);
			if (shouldReset) {
				metacraft_core$resetMusicTimer();
			}
		}
	}

	@Unique
	private void removeMusicPoint() {
		pointAttachment = null;
		point = null;
		if (pointHolder != null) {
			pointHolder.destroy();
		}
		pointHolder = null;
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		removeMusicPoint();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (pointAttachment != null) {
			pointAttachment.tick();
			if (pointAttachment.getWorld() != this.getEntityWorld()) {
				removeMusicPoint();
			}
		}

		if (music != null) {
			refreshMusicPoint(true);
		} else if (pointHolder != null) {
			removeMusicPoint();
		}

		getEntityWorld().getBiome(this.getBlockPos()).value().getMusic().ifPresent(music -> {
			for (var musicEntry : music.getEntries()) {
				if (potentiallyPlayingMusic.containsKey(musicEntry.value().sound())) {
					potentiallyPlayingMusic.get(musicEntry.value().sound()).setValue(musicEntry.value().minDelay());
				} else {
					potentiallyPlayingMusic.put(musicEntry.value().sound(), new MutableInt(musicEntry.value().minDelay()));
				}
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
				metacraft_core$stopMusic(null);
				musicStopTimer = -1;
			}
		} else {
			musicStopTimer = -1;
		}

		if (displayTimer > 0) {
			if (currentEntry != null) {
				currentEntry.credit().ifPresent(credit -> {
					this.sendMessage(credit.text(), true);
				});
				displayTimer--;
			} else {
				displayTimer = 0;
			}
		}

		long currentTime = System.currentTimeMillis() + networkHandler.getLatency();
		if (music != null && currentTime >= musicStartTime + musicLengthMillis - 50) {
			playMusic(false, true, musicStartTime + musicLengthMillis - networkHandler.getLatency());
		}

		if (music != null) {
			disableVanillaMusic();
		}
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.music = ((MixinServerPlayerEntity) (Object) oldPlayer).music;
		this.musicStartTime = ((MixinServerPlayerEntity) (Object) oldPlayer).musicStartTime;
		this.musicLengthMillis = ((MixinServerPlayerEntity) (Object) oldPlayer).musicLengthMillis;
		this.displayTimer = ((MixinServerPlayerEntity) (Object) oldPlayer).displayTimer;
		this.shouldContinuePlayingMusic = ((MixinServerPlayerEntity) (Object) oldPlayer).shouldContinuePlayingMusic;
		this.musicStopTimer = ((MixinServerPlayerEntity) (Object) oldPlayer).musicStopTimer;
		this.musicEntryQueue.addAll(((MixinServerPlayerEntity) (Object) oldPlayer).musicEntryQueue);
		this.musicEntriesInQueue.putAll(((MixinServerPlayerEntity) (Object) oldPlayer).musicEntriesInQueue);
		this.skipQueue = ((MixinServerPlayerEntity) (Object) oldPlayer).skipQueue;
		this.seenCreditFor.addAll(((MixinServerPlayerEntity) (Object) oldPlayer).seenCreditFor);

		if (oldPlayer.getEntityWorld() == getEntityWorld()) {
			this.point = ((MixinServerPlayerEntity) (Object) oldPlayer).point;
			this.pointHolder = ((MixinServerPlayerEntity) (Object) oldPlayer).pointHolder;
			if (pointHolder != null) {
				pointAttachment = new ManualAttachment(pointHolder, getEntityWorld(), this::getEntityPos);
			}
		} else {
			TaskScheduler.scheduleImmediately(getEntityWorld().getServer(), this::metacraft_core$resetMusicTimer);
		}

		if (!server.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			for (int i = 0; i < oldPlayer.getInventory().size(); i++) {
				var stack = oldPlayer.getInventory().getStack(i);
				if (stack.contains(METAcraftComponents.SOULBOUND)) {
					this.getInventory().setStack(i, stack);
				}
			}
		}
	}

	@Inject(method = "writeCustomData", at = @At("HEAD"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		nbt.putBoolean(ARE_BLOCKS_MOVABLE, movable);
		nbt.put(PREFERENCES, PreferenceData.CODEC, preferenceData);
	}

	@Inject(method = "readCustomData", at = @At("HEAD"))
	public void readNbt(ReadView nbt, CallbackInfo ci) {
		movable = nbt.getBoolean(ARE_BLOCKS_MOVABLE, false);
		nbt.read(
				PREFERENCES, PreferenceData.CODEC
		).ifPresent(preferenceData::applyFrom);
	}

	@ModifyReturnValue(method = "getRespawnTarget", at = @At("RETURN"))
	public TeleportTarget forcedRespawnPos(
			TeleportTarget original,
			@Local(argsOnly = true) TeleportTarget.PostDimensionTransition postDimensionTransition
	) {
		METAcraftCoreData data = METAcraftCoreData.getInstance(this.server);
		Vec3d pos = data.getForcedRespawnPos();
		ServerWorld world = Optional.ofNullable(data.getForcedRespawnWorld()).map(server::getWorld).orElse(null);
		if (pos != null && world != null) {
			return new TeleportTarget(world, pos, Vec3d.ZERO, data.getForcedRespawnAngle(), 0, postDimensionTransition);
		}
		return original;
	}

	@Unique
	private void disableVanillaMusic() {
		this.networkHandler.sendPacket(new StopSoundS2CPacket(
				SoundEvents.MUSIC_GAME.value().id(), SoundCategory.MUSIC
		));
		this.networkHandler.sendPacket(new StopSoundS2CPacket(
				SoundEvents.MUSIC_UNDER_WATER.value().id(), SoundCategory.MUSIC
		));
		if (this.isCreative()) {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					SoundEvents.MUSIC_CREATIVE.value().id(), SoundCategory.MUSIC
			));
		}
		if (this.getEntityWorld().getRegistryKey() == World.END) {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					SoundEvents.MUSIC_END.value().id(), SoundCategory.MUSIC
			));
		}
		potentiallyPlayingMusic.keySet().forEach(music -> {
			this.networkHandler.sendPacket(new StopSoundS2CPacket(
					music.value().id(), SoundCategory.MUSIC
			));
		});
		potentiallyPlayingMusic.clear();
	}

	@Unique
	private static final MarkerEntity PASSTHROUGH = new MarkerEntity(EntityType.MARKER, null);

	@Unique
	private void playMusic(boolean stopOnRestart, boolean canBeLoop, long startTimeServerside) {
		if (this.music != null) {
			if (currentEntry != null) {
				if (currentEntry.getMusic(inIntro).forceStop()) {
					stopOnRestart = true;
				}
			}
			var musicEntry = inIntro ? currentEntry : this.music.music().get(getRandom());

			boolean playIntro = !canBeLoop || musicEntry != currentEntry;

			if (musicEntry != currentEntry && musicEntry.credit().isPresent() && !seenCreditFor.contains(musicEntry)) {
				displayTimer = musicEntry.credit().get().displayTime();
				seenCreditFor.add(musicEntry);
			}

			this.currentEntry = musicEntry;
			var music = musicEntry.getMusic(playIntro);
			refreshMusicPoint(false);
			var actualTime = Math.max(System.currentTimeMillis(), startTimeServerside);
			this.musicStartTime = actualTime + networkHandler.getLatency();
			this.musicLengthMillis = (int) Math.round(music.length() * 1000);
			this.inIntro = playIntro && musicEntry.intro().isPresent();
			PASSTHROUGH.setId(point.getEntityId());
			Packet<? super ClientPlayPacketListener> packet = new PlaySoundFromEntityS2CPacket(
					music.music(), SoundCategory.MUSIC, PASSTHROUGH, 1, music.pitch(), this.getRandom().nextLong()
			);
			if (stopOnRestart) {
				packet = new BundleS2CPacket(
						List.of(
								new StopSoundS2CPacket(null, SoundCategory.MUSIC),
								packet
						)
				);
			}
			if (startTimeServerside <= 0 || System.currentTimeMillis() >= actualTime) {
				this.networkHandler.sendPacket(packet);
			} else {
				MusicTimerTracker.getTimer(getEntityWorld().getServer()).schedule(
						new MusicTimerTracker.SendPacketTask((ServerPlayerEntity) (Object) this, musicEntry, packet),
						actualTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS
				);
			}
		}
	}

	@Unique
	private void addToQueue(PlayerMusic entry, Predicate<ServerPlayerEntity> predicate) {
		if (!musicEntriesInQueue.containsKey(entry)) {
			musicEntriesInQueue.put(entry, predicate);
			musicEntryQueue.add(entry);
		}
	}

	@Unique
	private Pair<PlayerMusic, Predicate<ServerPlayerEntity>> grabFromQueue() {
		var entry = musicEntryQueue.poll();
		Predicate<ServerPlayerEntity> pred = null;
		if (entry != null) {
			pred = musicEntriesInQueue.remove(entry);
		}
		return entry != null ? Pair.of(entry, pred) : null;
	}

	@Unique
	private void stopCurrentMusic(boolean shouldPlaySomethingElse) {
		if (currentEntry != null) {
			var music = this.currentEntry.getMusic(inIntro);
			this.networkHandler.sendPacket(new StopSoundS2CPacket(music.music().value().id(), SoundCategory.MUSIC));
			if (pointHolder != null && !shouldPlaySomethingElse) {
				removeMusicPoint();
			}
			if (shouldPlaySomethingElse && !skipQueue && this.music != null) {
				addToQueue(this.music, shouldContinuePlayingMusic);
			}
		}
	}

	@Override
	public void metacraft_core$replacePredicate(PlayerMusic entry, Predicate<ServerPlayerEntity> predicate) {
		if (Objects.equals(entry, music)) {
			shouldContinuePlayingMusic = predicate;
		}
		if (musicEntriesInQueue.containsKey(entry)) {
			musicEntriesInQueue.put(entry, predicate);
		}
	}

	@Override
	public void metacraft_core$playMusic(PlayerMusic entry, boolean skipQueue, Predicate<ServerPlayerEntity> predicate) {
		if (entry == null) return;
		if (entry.equals(music)) {
			this.shouldContinuePlayingMusic = predicate;
			return;
		}
		if ((music == null || entry.priority() > music.priority())) {
			stopCurrentMusic(true);
			this.skipQueue = skipQueue;
			this.music = entry;
			playMusic(true, false, 0);
			this.shouldContinuePlayingMusic = predicate;
		} else if (!skipQueue) {
			addToQueue(entry, predicate);
		}
	}

	@Override
	public void metacraft_core$stopMusic(PlayerMusic entry) {
		if (music != null) {
			if (Objects.equals(music, entry) || entry == null) {
				stopCurrentMusic(false);
				this.music = null;
				var existing = grabFromQueue();
				if (existing != null) {
					metacraft_core$playMusic(existing.getFirst(), false, existing.getSecond());
					this.skipQueue = false;
				}
			} else if (musicEntriesInQueue.containsKey(entry)) {
				musicEntriesInQueue.remove(entry);
				musicEntryQueue.remove(entry);
			}
		}
	}

	@Override
	public void metacraft_core$clearAllMusic() {
		musicEntryQueue.clear();
		musicEntriesInQueue.clear();
		metacraft_core$stopMusic(null);
	}

	@Override
	public boolean metacraft_core$hasMusic(PlayerMusic entry) {
		return Objects.equals(entry, music);
	}

	@Override
	public boolean metacraft_core$hasMusicEntry(MusicEntry entry) {
		return Objects.equals(entry, currentEntry);
	}

	@Override
	public void metacraft_core$resetMusicTimer() {
		if (this.music != null) {
			playMusic(true, false, 0);
		}
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
	public PreferenceData metacraft_core$getPreferences() {
		return preferenceData;
	}
}
