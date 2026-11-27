package se.metacraft.portable_jukebox;

import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.data.HasBoombox;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;

public class EntityRefHelper {

	public static void addBoombox(EntityRef ref, BoomboxEntity portable) {
		ref.getCasted(HasBoombox.class).ifPresent(attachment -> {
			attachment.portable_jukebox$addBoombox(portable);
			portable.setConnectedEntity(ref);
		});
	}

	public static void removeBoombox(EntityRef ref, BoomboxEntity portable) {
		ref.getCasted(HasBoombox.class).ifPresent(
				attachment -> attachment.portable_jukebox$removeBoombox(portable)
		);
	}

}
