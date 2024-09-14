package se.datasektionen.mc.zones.compat.music;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;
import se.datasektionen.mc.zones.compat.CoreTypes;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataEntityTracking;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

import java.util.Optional;

public class MusicData extends ZoneDataEntityTracking {

	public static final MapCodec<MusicData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MusicEntry.CODEC.optionalFieldOf("music").forGetter(d -> d.music)
			).apply(instance, MusicData::new)
	);

	private Optional<MusicEntry> music;

	public MusicData(Optional<MusicEntry> music) {
		this.music = music;
	}

	public void setMusic(Optional<MusicEntry> music) {
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
		return "MusicData[" + music.map(MusicEntry::toString).orElse("") + "]";
	}

}
