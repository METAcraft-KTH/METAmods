package se.datasektionen.mc.metacraft_core.music;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;

import java.util.Optional;

public class BossBarMusicHandler {

	private MusicEntry music;

	private final ServerBossBar bossbar;

	public BossBarMusicHandler(ServerBossBar bossbar) {
		this.bossbar = bossbar;
	}

	public void onPlayerAdded(ServerPlayerEntity player) {
		if (music != null && bossbar.isVisible()) {
			MusicHelper.playMusic(player, music);
		}
	}

	public void onPlayerRemoved(ServerPlayerEntity player) {
		if (music != null && bossbar.isVisible()) {
			MusicHelper.replacePredicate(player, music, LivingEntity::isDead);
		}
	}

	public void onToggleVisibility(boolean visible) {
		if (music != null) {
			if (visible) {
				for (var player : bossbar.getPlayers()) {
					MusicHelper.playMusic(player, music);
				}
			} else {
				for (var player : bossbar.getPlayers()) {
					MusicHelper.stopMusic(player, music);
				}
			}
		}
	}

	public void setMusic(MusicEntry music) {
		var prevMusic = this.music;
		this.music = music;
		if (music != prevMusic) {
			for (var player : bossbar.getPlayers()) {
				MusicHelper.replaceMusic(player, prevMusic, music);
			}
		}
	}

	public Optional<MusicEntry> getMusic() {
		return Optional.ofNullable(music);
	}
}
