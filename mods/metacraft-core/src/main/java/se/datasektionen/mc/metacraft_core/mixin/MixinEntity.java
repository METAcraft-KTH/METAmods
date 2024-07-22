package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;
import se.datasektionen.mc.metacraft_core.music.ServerBossBarWithMusic;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtensions {

	@Shadow public abstract Text getDisplayName();

	@Shadow public abstract World getWorld();

	@Shadow public abstract int getId();

	@Shadow @Nullable public abstract MinecraftServer getServer();

	@Unique
	private static final String BOSS_BAR = "BossBar";
	@Unique
	private static final String COLOUR = "Colour";
	@Unique
	private static final String STYLE = "Style";

	@Unique
	private static final String VISIBLE = "Visible";
	@Unique
	private static final String DARKEN_SKY = "DarkenSky";
	@Unique
	private static final String THICKEN_FOG = "ThickenFog";

	@Unique
	private static final String MUSIC = "Music";


	@Unique
	private static final String VALUE = "Value";

	@Unique
	private static final String MAX = "Max";

	@Unique
	private ServerBossBarWithMusic bossBar;

	@Unique
	private int value;
	@Unique
	private int max;

	@Unique
	private void removeBossBar() {
		if (this.bossBar != null) {
			this.bossBar.clearPlayers();
			this.bossBar = null;
		}
	}

	@Unique
	private void initialiseBossBar() {
		if (bossBar == null || !(this.getWorld() instanceof ServerWorld sw)) return;
		var tracker = EntityTrackerHelper.getEntityTrackers(sw).get(this.getId());
		if (tracker != null) {
			for (var player : EntityTrackerHelper.getListeners(tracker)) {
				this.bossBar.addPlayer(player.getPlayer());
			}
		}
	}

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		metacraft_lib$loadBossBar(nbt);
	}

	@Inject(method = "setCustomName", at = @At("RETURN"))
	public void setCustomName(Text name, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.setName(name);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (this.bossBar != null && this.bossBar.isTracking()) {
			if ((Object) this instanceof LivingEntity living) {
				this.bossBar.setPercent(living.getHealth() / living.getMaxHealth());
			}
		}
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		metacraft_lib$saveBossBar(nbt);
	}

	@Override
	public void metacraft_lib$loadBossBar(NbtCompound nbt) {
		if (nbt.contains(BOSS_BAR)) {
			NbtCompound bossBar = nbt.getCompound(BOSS_BAR);
			if (this.bossBar == null) {
				this.bossBar = new ServerBossBarWithMusic(
						this.getDisplayName(),
						BossBar.Color.byName(bossBar.getString(COLOUR)),
						BossBar.Style.byName(bossBar.getString(STYLE))
				);
				initialiseBossBar();
			} else {
				this.bossBar.setName(this.getDisplayName());
				this.bossBar.setColor(BossBar.Color.byName(bossBar.getString(COLOUR)));
				this.bossBar.setStyle(BossBar.Style.byName(bossBar.getString(STYLE)));
			}
			if (bossBar.contains(VALUE)) {
				NbtCompound value = bossBar.getCompound(VALUE);
				this.value = value.getInt(VALUE);
				this.max = value.getInt(MAX);
				this.bossBar.setPercent((float) this.value / this.max);
				this.bossBar.setTracking(false);
			} else if ((Object) this instanceof LivingEntity living) {
				this.bossBar.setPercent(living.getHealth() / living.getMaxHealth());
				this.bossBar.setTracking(true);
			} else {
				this.bossBar.setTracking(false);
			}
			if (bossBar.contains(DARKEN_SKY)) {
				this.bossBar.setDarkenSky(bossBar.getBoolean(DARKEN_SKY));
			}
			if (bossBar.contains(THICKEN_FOG)) {
				this.bossBar.setThickenFog(bossBar.getBoolean(THICKEN_FOG));
			}
			if (bossBar.contains(VISIBLE)) {
				this.bossBar.setVisible(bossBar.getBoolean(VISIBLE));
			}
			if (bossBar.contains(MUSIC)) {
				MusicEntry.CODEC.parse(NbtOps.INSTANCE, bossBar.get(MUSIC)).resultOrPartial(
						METAcraftCore.LOGGER::error
				).ifPresent(music -> {
					this.bossBar.setMusic(music);
				});
			} else {
				this.bossBar.setMusic(null);
			}
		} else {
			removeBossBar();
		}
	}

	@Override
	public void metacraft_lib$saveBossBar(NbtCompound nbt) {
		if (bossBar != null) {
			NbtCompound bossBar = new NbtCompound();
			bossBar.putString(COLOUR, this.bossBar.getColor().getName());
			bossBar.putString(STYLE, this.bossBar.getStyle().getName());
			bossBar.putBoolean(DARKEN_SKY, this.bossBar.shouldDarkenSky());
			bossBar.putBoolean(THICKEN_FOG, this.bossBar.shouldThickenFog());
			bossBar.putBoolean(VISIBLE, this.bossBar.isVisible());
			this.bossBar.getMusic().flatMap(music -> MusicEntry.CODEC.encodeStart(NbtOps.INSTANCE, music).resultOrPartial(
					METAcraftCore.LOGGER::error
			)).ifPresent(music -> {
				bossBar.put(MUSIC, music);
			});
			if (!this.bossBar.isTracking()) {
				NbtCompound value = new NbtCompound();
				value.putInt(VALUE, this.value);
				value.putInt(MAX, this.max);
			}
			nbt.put(BOSS_BAR, bossBar);
		}
	}

	@Inject(method = "onStartedTrackingBy", at = @At("HEAD"))
	public void onStartTracking(ServerPlayerEntity player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.addPlayer(player);
		}
	}

	@Inject(method = "onStoppedTrackingBy", at = @At("HEAD"))
	public void onStopTracking(ServerPlayerEntity player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.removePlayer(player);
		}
	}

	@Override
	public Optional<ServerBossBarWithMusic> metacraft_lib$getBossBar() {
		return Optional.ofNullable(bossBar);
	}

	@Override
	public void metacraft_lib$setBossBar(ServerBossBarWithMusic bossBar) {
		if (this.bossBar != bossBar) {
			removeBossBar();
			this.bossBar = bossBar;
			initialiseBossBar();
		}
	}

}
