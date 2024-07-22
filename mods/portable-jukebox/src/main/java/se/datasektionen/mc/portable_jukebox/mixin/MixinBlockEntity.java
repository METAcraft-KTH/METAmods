package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.data.HasPortableJukebox;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

import java.util.HashSet;
import java.util.Set;

@Mixin(BlockEntity.class)
public class MixinBlockEntity implements HasPortableJukebox {

	@Unique
	private final Set<PortableJukeboxEntity> jukeboxes = new HashSet<>();

	@Override
	public void portable_jukebox$addPortableJukebox(PortableJukeboxEntity entity) {
		this.jukeboxes.add(entity);
		if (entity != null) {
			entity.setEntity(EntityRef.fromBlock((BlockEntity) (Object) this));
		}
	}

	@Override
	public void portable_jukebox$removePortableJukebox(PortableJukeboxEntity entity) {
		jukeboxes.remove(entity);
	}

	@Override
	public Set<PortableJukeboxEntity> portable_jukebox$getPortableJukeboxes() {
		return jukeboxes;
	}
}
