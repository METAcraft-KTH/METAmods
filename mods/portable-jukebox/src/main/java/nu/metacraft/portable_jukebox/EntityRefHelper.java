package nu.metacraft.portable_jukebox;

import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.data.HasPortableJukebox;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

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
