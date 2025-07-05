package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.data.HasPortableJukebox;
import nu.metacraft.portable_jukebox.data.RemovalAware;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

import java.util.*;

@Mixin(Entity.class)
public abstract class MixinEntity implements HasPortableJukebox {

	@Unique
	private final Set<PortableJukeboxEntity> portableJukeboxes = new HashSet<>();


	@Override
	public void portable_jukebox$addPortableJukebox(PortableJukeboxEntity entity) {
		this.portableJukeboxes.add(entity);
		if (entity != null) {
			entity.setEntity(EntityRef.fromEntity((Entity) (Object) this));
		}
	}

	@Override
	public void portable_jukebox$removePortableJukebox(PortableJukeboxEntity entity) {
		portableJukeboxes.remove(entity);
	}

	@Override
	public Set<PortableJukeboxEntity> portable_jukebox$getPortableJukeboxes() {
		return Collections.unmodifiableSet(portableJukeboxes);
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if (this instanceof RemovalAware aware) {
			aware.onEntityRemoved(reason);
		}
	}
}
