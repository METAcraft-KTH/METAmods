package se.metacraft.portable_jukebox.data;

import se.metacraft.portable_jukebox.entity.BoomboxEntity;

import java.util.Set;

public interface HasBoombox {

	void portable_jukebox$addBoombox(BoomboxEntity entity);
	void portable_jukebox$removeBoombox(BoomboxEntity entity);

	Set<BoomboxEntity> portable_jukebox$getBoomboxes();

}
