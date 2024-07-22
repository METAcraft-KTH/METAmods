package se.datasektionen.mc.portable_jukebox.data;

import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

import java.util.Set;

public interface HasPortableJukebox {

	void portable_jukebox$addPortableJukebox(PortableJukeboxEntity entity);
	void portable_jukebox$removePortableJukebox(PortableJukeboxEntity entity);

	Set<PortableJukeboxEntity> portable_jukebox$getPortableJukeboxes();

}
