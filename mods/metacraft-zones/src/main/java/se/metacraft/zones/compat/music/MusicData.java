package se.metacraft.zones.compat.music;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.core.music.PlayerMusic;
import se.metacraft.core.util.helper.MusicHelper;
import se.metacraft.zones.compat.CoreTypes;
import se.metacraft.zones.zone.data.ZoneData;
import se.metacraft.zones.zone.data.ZoneDataEntityTracking;
import se.metacraft.zones.zone.data.ZoneDataType;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class MusicData extends ZoneDataEntityTracking {

	public static final MapCodec<MusicData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerMusic.EASY_CODEC.optionalFieldOf("music").forGetter(d -> d.music)
			).apply(instance, MusicData::new)
	);

	private Optional<PlayerMusic> music;

	public MusicData(Optional<PlayerMusic> music) {
		this.music = music;
	}

	public void setMusic(Optional<PlayerMusic> music) {
		this.music.ifPresent(musicEntry -> zone.getEntities().forEach(e -> {
			if (e instanceof ServerPlayer p && MusicHelper.isMusicPlaying(p, musicEntry)) {
				MusicHelper.stopMusic(p);
			}
		}));
		this.music = music;
		this.music.ifPresent(musicEntry -> zone.getEntities().forEach(e -> {
			if (e instanceof ServerPlayer p) {
				MusicHelper.playMusic(p, musicEntry);
			}
		}));
		markDirty();
	}

	@Override
	public void onEnter(Entity entity) {
		music.ifPresent(music -> {
			if (entity instanceof ServerPlayer p) {
				MusicHelper.playMusic(p, music);
			}
		});
	}

	@Override
	public void onLeave(Entity entity) {
		music.ifPresent(music -> {
			if (entity instanceof ServerPlayer p && MusicHelper.isMusicPlaying(p, music)) {
				MusicHelper.stopMusic(p);
			}
		});
	}

	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return CoreTypes.MUSIC;
	}

	@Override
	public String toString() {
		return "MusicData[" + music.map(PlayerMusic::toString).orElse("") + "]";
	}

}
