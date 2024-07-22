package se.datasektionen.mc.portable_jukebox;

import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.data.HasPortableJukebox;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

public class EntityRefHelper {

	public static void addPortableJukebox(EntityRef ref, PortableJukeboxEntity portable) {
		ref.getCasted(HasPortableJukebox.class).ifPresent(
				attachment -> attachment.portable_jukebox$addPortableJukebox(portable)
		);
	}

	public static void removePortableJukebox(EntityRef ref, PortableJukeboxEntity portable) {
		ref.getCasted(HasPortableJukebox.class).ifPresent(
				attachment -> attachment.portable_jukebox$removePortableJukebox(portable)
		);
	}

}
