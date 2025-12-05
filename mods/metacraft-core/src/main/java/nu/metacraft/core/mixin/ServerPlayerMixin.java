package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.music.PlayerMusic;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.extensions.ServerPlayerExtensions;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.core.music.MusicTimerTracker;
import nu.metacraft.core.preferences.PreferenceData;
import nu.metacraft.core.util.METAcraftCoreData;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ServerPlayerExtensions {

	@Shadow public ServerGamePacketListenerImpl connection;

	public ServerPlayerMixin(Level world, GameProfile profile) {
		super(world, profile);
	}

	@Shadow public abstract void displayClientMessage(Component message, boolean overlay);

	@Shadow @Final
	private MinecraftServer server;

	@Shadow public abstract ServerLevel level();

	@Unique
	private static final String ARE_BLOCKS_MOVABLE = "AreBlocksMovable";

	@Unique
	private static final String PREFERENCES = "metacraft:preferences";


	@Unique
	private final PreferenceData preferenceData = new PreferenceData();

	@Unique
	private boolean movable = false;

	@Unique
	private final Map<Holder<SoundEvent>, MutableInt> potentiallyPlayingMusic = new HashMap<>();

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
	private boolean seenMusicInfo = false;

	@Unique
	private final Queue<PlayerMusic> musicEntryQueue = new PriorityQueue<>();

	@Unique
	private final Map<PlayerMusic, Predicate<ServerPlayer>> musicEntriesInQueue = new HashMap<>();


	@Unique
	private Predicate<ServerPlayer> shouldContinuePlayingMusic = player -> true;

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
			pointHolder.startWatching((ServerPlayer) (Object) this);
			pointAttachment = new ManualAttachment(pointHolder, level(), this::position);
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

	@Inject(method = "disconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		removeMusicPoint();
	}

	@Unique
	private void addPotentialMusic(Music musicEntry) {
		if (potentiallyPlayingMusic.containsKey(musicEntry.sound())) {
			potentiallyPlayingMusic.get(musicEntry.sound()).setValue(musicEntry.minDelay());
		} else {
			potentiallyPlayingMusic.put(musicEntry.sound(), new MutableInt(musicEntry.minDelay()));
		}
	}
	
	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (pointAttachment != null) {
			pointAttachment.tick();
			if (pointAttachment.getWorld() != this.level()) {
				removeMusicPoint();
			}
		}

		if (music != null) {
			refreshMusicPoint(true);
		} else if (pointHolder != null) {
			removeMusicPoint();
		}

		var music = level().environmentAttributes().getValue(EnvironmentAttributes.BACKGROUND_MUSIC, position());
		music.defaultMusic().ifPresent(this::addPotentialMusic);
		music.creativeMusic().ifPresent(this::addPotentialMusic);
		music.underwaterMusic().ifPresent(this::addPotentialMusic);
		
		potentiallyPlayingMusic.keySet().removeIf(m -> {
			return potentiallyPlayingMusic.get(m).decrementAndGet() <= 0;
		});

		if (!shouldContinuePlayingMusic.test((ServerPlayer) (Object) this)) {
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
					this.displayClientMessage(credit.text(), true);
				});
				displayTimer--;
			} else {
				displayTimer = 0;
			}
		}

		long currentTime = System.currentTimeMillis() + connection.latency();
		if (music != null && currentTime >= musicStartTime + musicLengthMillis - 50) {
			playMusic(false, true, musicStartTime + musicLengthMillis - connection.latency());
		}

		if (music != null) {
			disableVanillaMusic();
		}
	}

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		this.music = ((ServerPlayerMixin) (Object) oldPlayer).music;
		this.musicStartTime = ((ServerPlayerMixin) (Object) oldPlayer).musicStartTime;
		this.musicLengthMillis = ((ServerPlayerMixin) (Object) oldPlayer).musicLengthMillis;
		this.displayTimer = ((ServerPlayerMixin) (Object) oldPlayer).displayTimer;
		this.shouldContinuePlayingMusic = ((ServerPlayerMixin) (Object) oldPlayer).shouldContinuePlayingMusic;
		this.musicStopTimer = ((ServerPlayerMixin) (Object) oldPlayer).musicStopTimer;
		this.musicEntryQueue.addAll(((ServerPlayerMixin) (Object) oldPlayer).musicEntryQueue);
		this.musicEntriesInQueue.putAll(((ServerPlayerMixin) (Object) oldPlayer).musicEntriesInQueue);
		this.skipQueue = ((ServerPlayerMixin) (Object) oldPlayer).skipQueue;
		this.seenCreditFor.addAll(((ServerPlayerMixin) (Object) oldPlayer).seenCreditFor);
		this.seenMusicInfo = ((ServerPlayerMixin) (Object) oldPlayer).seenMusicInfo;

		if (oldPlayer.level() == level()) {
			this.point = ((ServerPlayerMixin) (Object) oldPlayer).point;
			this.pointHolder = ((ServerPlayerMixin) (Object) oldPlayer).pointHolder;
			if (pointHolder != null) {
				pointAttachment = new ManualAttachment(pointHolder, level(), this::position);
			}
		} else {
			TaskScheduler.scheduleImmediately(level().getServer(), this::metacraft_core$resetMusicTimer);
		}

		if (!level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			for (int i = 0; i < oldPlayer.getInventory().getContainerSize(); i++) {
				var stack = oldPlayer.getInventory().getItem(i);
				if (stack.has(METAcraftComponents.SOULBOUND)) {
					this.getInventory().setItem(i, stack);
				}
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(ARE_BLOCKS_MOVABLE, movable);
		nbt.store(PREFERENCES, PreferenceData.CODEC, preferenceData);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
	public void readNbt(ValueInput nbt, CallbackInfo ci) {
		movable = nbt.getBooleanOr(ARE_BLOCKS_MOVABLE, false);
		nbt.read(
				PREFERENCES, PreferenceData.CODEC
		).ifPresent(preferenceData::applyFrom);
	}

	@ModifyReturnValue(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"))
	public TeleportTransition forcedRespawnPos(
			TeleportTransition original,
			@Local(argsOnly = true) TeleportTransition.PostTeleportTransition postDimensionTransition
	) {
		METAcraftCoreData data = METAcraftCoreData.getInstance(this.server);
		Vec3 pos = data.getForcedRespawnPos();
		ServerLevel world = Optional.ofNullable(data.getForcedRespawnWorld()).map(server::getLevel).orElse(null);
		if (pos != null && world != null) {
			return new TeleportTransition(world, pos, Vec3.ZERO, data.getForcedRespawnAngle(), 0, postDimensionTransition);
		}
		return original;
	}

	@Unique
	private void disableVanillaMusic() {
		this.connection.send(new ClientboundStopSoundPacket(
				SoundEvents.MUSIC_GAME.value().location(), SoundSource.MUSIC
		));
		this.connection.send(new ClientboundStopSoundPacket(
				SoundEvents.MUSIC_UNDER_WATER.value().location(), SoundSource.MUSIC
		));
		if (this.isCreative()) {
			this.connection.send(new ClientboundStopSoundPacket(
					SoundEvents.MUSIC_CREATIVE.value().location(), SoundSource.MUSIC
			));
		}
		if (this.level().dimension() == Level.END) {
			this.connection.send(new ClientboundStopSoundPacket(
					SoundEvents.MUSIC_END.value().location(), SoundSource.MUSIC
			));
		}
		potentiallyPlayingMusic.keySet().forEach(music -> {
			this.connection.send(new ClientboundStopSoundPacket(
					music.value().location(), SoundSource.MUSIC
			));
		});
		potentiallyPlayingMusic.clear();
	}

	@Unique
	private static final Marker PASSTHROUGH = new Marker(EntityType.MARKER, null);

	@Unique
	private void playMusic(boolean stopOnRestart, boolean canBeLoop, long startTimeServerside) {
		if (this.music != null) {
			if (currentEntry != null) {
				if (currentEntry.getMusic(inIntro).forceStop()) {
					stopOnRestart = true;
				}
			}
			var musicEntry = inIntro && canBeLoop ? currentEntry : this.music.music().getRandomOrThrow(getRandom());

			boolean playIntro = !canBeLoop || musicEntry != currentEntry;

			if (musicEntry != currentEntry && musicEntry.credit().isPresent() && !seenCreditFor.contains(musicEntry)) {
				displayTimer = musicEntry.credit().get().displayTime();
				seenCreditFor.add(musicEntry);
			}

			this.currentEntry = musicEntry;
			var music = musicEntry.getMusic(playIntro);
			refreshMusicPoint(false);
			var actualTime = Math.max(System.currentTimeMillis(), startTimeServerside);
			this.musicStartTime = actualTime + connection.latency();
			this.musicLengthMillis = (int) Math.round(music.length() * 1000);
			this.inIntro = playIntro && musicEntry.intro().isPresent();
			PASSTHROUGH.setId(point.getEntityId());
			Packet<? super ClientGamePacketListener> packet = new ClientboundSoundEntityPacket(
					music.music(), SoundSource.MUSIC, PASSTHROUGH, 1, music.pitch(), this.getRandom().nextLong()
			);
			if (stopOnRestart) {
				packet = new ClientboundBundlePacket(
						List.of(
								new ClientboundStopSoundPacket(null, SoundSource.MUSIC),
								packet
						)
				);
			}
			if (startTimeServerside <= 0 || System.currentTimeMillis() >= actualTime) {
				this.connection.send(packet);
			} else {
				MusicTimerTracker.getTimer(level().getServer()).schedule(
						new MusicTimerTracker.SendPacketTask((ServerPlayer) (Object) this, musicEntry, packet),
						actualTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS
				);
			}
			if (!seenMusicInfo) {
				var msg = Component.literal("").append(
						Component.literal(" \uD83D\uDEC8 ").withStyle(style -> style.withColor(ChatFormatting.AQUA))
				).append(
						"Custom music started playing. If you cannot hear it, check your music volume in settings and run "
				).append(
						Component.literal("/reset-music").withStyle(
								style -> style.withClickEvent(
										new ClickEvent.SuggestCommand("/reset-music")
								).withColor(ChatFormatting.GREEN)
						)
				);
				displayClientMessage(msg, false);
				connection.send(new ClientboundSetTitleTextPacket(Component.literal("Custom Music!!!!")));
				connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("See chat for details")));
				seenMusicInfo = true;
			}
		}
	}

	@Unique
	private void addToQueue(PlayerMusic entry, Predicate<ServerPlayer> predicate) {
		if (!musicEntriesInQueue.containsKey(entry)) {
			musicEntriesInQueue.put(entry, predicate);
			musicEntryQueue.add(entry);
		}
	}

	@Unique
	private Pair<PlayerMusic, Predicate<ServerPlayer>> grabFromQueue() {
		var entry = musicEntryQueue.poll();
		Predicate<ServerPlayer> pred = null;
		if (entry != null) {
			pred = musicEntriesInQueue.remove(entry);
		}
		return entry != null ? Pair.of(entry, pred) : null;
	}

	@Unique
	private void stopCurrentMusic(boolean shouldPlaySomethingElse) {
		if (currentEntry != null) {
			var music = this.currentEntry.getMusic(inIntro);
			this.connection.send(new ClientboundStopSoundPacket(music.music().value().location(), SoundSource.MUSIC));
			if (pointHolder != null && !shouldPlaySomethingElse) {
				removeMusicPoint();
			}
			if (shouldPlaySomethingElse && !skipQueue && this.music != null) {
				addToQueue(this.music, shouldContinuePlayingMusic);
			}
		}
	}

	@Override
	public void metacraft_core$replacePredicate(PlayerMusic entry, Predicate<ServerPlayer> predicate) {
		if (Objects.equals(entry, music)) {
			shouldContinuePlayingMusic = predicate;
		}
		if (musicEntriesInQueue.containsKey(entry)) {
			musicEntriesInQueue.put(entry, predicate);
		}
	}

	@Override
	public void metacraft_core$playMusic(PlayerMusic entry, boolean skipQueue, Predicate<ServerPlayer> predicate) {
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
