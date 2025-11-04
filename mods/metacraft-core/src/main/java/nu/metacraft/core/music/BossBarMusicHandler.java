package nu.metacraft.core.music;

import nu.metacraft.core.util.helper.MusicHelper;

import java.util.Optional;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class BossBarMusicHandler {

	private PlayerMusic music;

	private final ServerBossEvent bossbar;

	public BossBarMusicHandler(ServerBossEvent bossbar) {
		this.bossbar = bossbar;
	}

	public void onPlayerAdded(ServerPlayer player) {
		if (music != null && bossbar.isVisible()) {
			MusicHelper.playMusic(player, music);
		}
	}

	public void onPlayerRemoved(ServerPlayer player) {
		if (music != null && bossbar.isVisible()) {
			MusicHelper.replacePredicate(player, music, LivingEntity::isDeadOrDying);
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

	public void setMusic(PlayerMusic music) {
		var prevMusic = this.music;
		this.music = music;
		if (music != prevMusic) {
			for (var player : bossbar.getPlayers()) {
				MusicHelper.replaceMusic(player, prevMusic, music);
			}
		}
	}

	public Optional<PlayerMusic> getMusic() {
		return Optional.ofNullable(music);
	}
}
