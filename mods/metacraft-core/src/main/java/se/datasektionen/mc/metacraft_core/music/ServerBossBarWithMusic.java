package se.datasektionen.mc.metacraft_core.music;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;

import java.util.Optional;

public class ServerBossBarWithMusic extends ServerBossBar {

	public static final String BOSS_BAR = "BossBar";

	private static final String NAME = "name";
	private static final String COLOUR = "color";
	private static final String STYLE = "style";

	private static final String VISIBLE = "visible";

	private static final String DARKEN_SKY = "darken_sky";

	private static final String THICKEN_FOG = "thicken_fog";

	private static final String MUSIC = "music";

	private static final String VALUE = "value";

	private static final String MAX = "max";
	
	private MusicEntry music;

	private boolean trackingHealth = true;
	private boolean trackingName = true;

	private int value;
	private int max;

	public ServerBossBarWithMusic(Text displayName, Color color, Style style) {
		super(displayName, color, style);
	}

	public void setMusic(MusicEntry music) {
		var prevMusic = this.music;
		this.music = music;
		if (music != prevMusic) {
			for (var player : getPlayers()) {
				((ServerPlayerEntityExtensions) player).metacraft_lib$replaceMusic(prevMusic, music);
			}
		}
	}

	public Optional<MusicEntry> getMusic() {
		return Optional.ofNullable(music);
	}

	public void addPlayer(ServerPlayerEntity player) {
		super.addPlayer(player);
		if (music != null && isVisible()) {
			((ServerPlayerEntityExtensions) player).metacraft_lib$setMusicEntry(music);
		}
	}

	public void removePlayer(ServerPlayerEntity player) {
		super.removePlayer(player);
		if (music != null && isVisible()) {
			((ServerPlayerEntityExtensions) player).metacraft_lib$setContinuePlayingMusicPredicate(LivingEntity::isDead);
		}
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (music != null) {
			if (visible) {
				for (var player : getPlayers()) {
					((ServerPlayerEntityExtensions) player).metacraft_lib$setMusicEntry(music);
				}
			} else {
				for (var player : getPlayers()) {
					if (((ServerPlayerEntityExtensions) player).metacraft_lib$hasMusicEntry(music, false)) {
						((ServerPlayerEntityExtensions) player).metacraft_lib$setMusicEntry(null);
					}
				}
			}
		}
	}

	public boolean isTrackingHealth() {
		return trackingHealth;
	}

	public boolean isTrackingName() {
		return trackingName;
	}

	public ServerBossBarWithMusic copy() {
		var bossBar = new ServerBossBarWithMusic(getName(), getColor(), getStyle());
		getMusic().ifPresent(bossBar::setMusic);
		bossBar.setDarkenSky(darkenSky);
		bossBar.setDragonMusic(dragonMusic);
		bossBar.trackingHealth = trackingHealth;
		bossBar.trackingName = trackingName;
		bossBar.setPercent(percent);
		bossBar.value = value;
		bossBar.max = max;
		bossBar.setThickenFog(thickenFog);
		bossBar.setVisible(isVisible());
		return bossBar;
	}
	
	public static ServerBossBarWithMusic create() {
		return new ServerBossBarWithMusic(Text.empty(), Color.WHITE, Style.PROGRESS);
	}

	public void updateFromEntity(Entity entity) {
		if (trackingName) {
			setName(entity.getDisplayName());
		}
		if (trackingHealth && entity instanceof LivingEntity living) {
			setPercent(living.getHealth() / living.getMaxHealth());
		}
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		final var ops = lookup.getOps(NbtOps.INSTANCE);
		if (nbt.contains(NAME)) {
			TextCodecs.CODEC.parse(
					ops, nbt.get(NAME)
			).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(this::setName);
			trackingName = false;
		} else {
			trackingName = true;
		}
		if (nbt.contains(COLOUR)) {
			this.setColor(BossBar.Color.byName(nbt.getString(COLOUR)));
		}
		if (nbt.contains(STYLE)) {
			this.setStyle(BossBar.Style.byName(nbt.getString(STYLE)));
		}
		if (nbt.contains(VALUE)) {
			this.value = nbt.getInt(VALUE);
			this.max = nbt.getInt(MAX);
			this.setPercent((float) this.value / this.max);
			trackingHealth = false;
		} else {
			trackingHealth = true;
		}
		if (nbt.contains(DARKEN_SKY)) {
			this.setDarkenSky(nbt.getBoolean(DARKEN_SKY));
		}
		if (nbt.contains(THICKEN_FOG)) {
			this.setThickenFog(nbt.getBoolean(THICKEN_FOG));
		}
		if (nbt.contains(VISIBLE)) {
			this.setVisible(nbt.getBoolean(VISIBLE));
		}
		if (nbt.contains(MUSIC)) {
			MusicEntry.CODEC.parse(ops, nbt.get(MUSIC)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(this::setMusic);
		} else {
			this.setMusic(null);
		}
	}

	public NbtCompound writeNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		final var ops = lookup.getOps(NbtOps.INSTANCE);
		nbt.putString(COLOUR, this.getColor().getName());
		nbt.putString(STYLE, this.getStyle().getName());
		nbt.putBoolean(DARKEN_SKY, this.shouldDarkenSky());
		nbt.putBoolean(THICKEN_FOG, this.shouldThickenFog());
		nbt.putBoolean(VISIBLE, this.isVisible());
		this.getMusic().flatMap(music -> MusicEntry.CODEC.encodeStart(ops, music).resultOrPartial(
				METAcraftCore.LOGGER::error
		)).ifPresent(music -> {
			nbt.put(MUSIC, music);
		});
		if (!this.isTrackingHealth()) {
			nbt.putInt(VALUE, this.value);
			nbt.putInt(MAX, this.max);
		}
		if (!trackingName) {
			TextCodecs.CODEC.encodeStart(ops, getName()).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(
					name -> nbt.put(NAME, name)
			);
		}
		return nbt;
	}

}
