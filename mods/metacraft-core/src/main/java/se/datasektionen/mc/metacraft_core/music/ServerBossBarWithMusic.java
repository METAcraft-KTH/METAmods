package se.datasektionen.mc.metacraft_core.music;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;

import java.util.Optional;

public class ServerBossBarWithMusic extends ServerBossBar {

	private MusicEntry music;

	private boolean tracking = true;

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

	public void setTracking(boolean tracking) {
		this.tracking = tracking;
	}

	public boolean isTracking() {
		return tracking;
	}

	public ServerBossBarWithMusic copy() {
		var bossBar = new ServerBossBarWithMusic(getName(), getColor(), getStyle());
		getMusic().ifPresent(bossBar::setMusic);
		bossBar.setDarkenSky(darkenSky);
		bossBar.setDragonMusic(dragonMusic);
		bossBar.setTracking(tracking);
		bossBar.setPercent(percent);
		bossBar.setThickenFog(thickenFog);
		bossBar.setVisible(isVisible());
		return bossBar;
	}

}
