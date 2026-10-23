package se.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import se.metacraft.portable_jukebox.data.HasPortableJukebox;
import se.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements HasPortableJukebox {

	@Unique
	private final Set<PortableJukeboxEntity> jukeboxes = new HashSet<>();

	@Override
	public void portable_jukebox$addPortableJukebox(PortableJukeboxEntity entity) {
		this.jukeboxes.add(entity);
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
