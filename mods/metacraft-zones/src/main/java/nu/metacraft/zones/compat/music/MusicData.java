package nu.metacraft.zones.compat.music;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.music.PlayerMusic;
import nu.metacraft.core.util.helper.MusicHelper;
import nu.metacraft.zones.compat.CoreTypes;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataEntityTracking;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.Optional;

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
			if (e instanceof ServerPlayerEntity p && MusicHelper.isMusicPlaying(p, musicEntry)) {
				MusicHelper.stopMusic(p);
			}
		}));
		this.music = music;
		this.music.ifPresent(musicEntry -> zone.getEntities().forEach(e -> {
			if (e instanceof ServerPlayerEntity p) {
				MusicHelper.playMusic(p, musicEntry);
			}
		}));
		markDirty();
	}

	@Override
	public void onEnter(Entity entity) {
		music.ifPresent(music -> {
			if (entity instanceof ServerPlayerEntity p) {
				MusicHelper.playMusic(p, music);
			}
		});
	}

	@Override
	public void onLeave(Entity entity) {
		music.ifPresent(music -> {
			if (entity instanceof ServerPlayerEntity p && MusicHelper.isMusicPlaying(p, music)) {
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
