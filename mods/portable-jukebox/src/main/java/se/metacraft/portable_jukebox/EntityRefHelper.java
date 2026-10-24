package se.metacraft.portable_jukebox;

import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.data.HasPortableJukebox;
import se.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

public class EntityRefHelper {

	public static void addPortableJukebox(EntityRef ref, PortableJukeboxEntity portable) {
		ref.getCasted(HasPortableJukebox.class).ifPresent(attachment -> {
			attachment.portable_jukebox$addPortableJukebox(portable);
			portable.setConnectedEntity(ref);
		});
	}

	public static void removePortableJukebox(EntityRef ref, PortableJukeboxEntity portable) {
		ref.getCasted(HasPortableJukebox.class).ifPresent(
				attachment -> attachment.portable_jukebox$removePortableJukebox(portable)
		);
	}

}
