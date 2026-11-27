package se.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.portable_jukebox.data.HasBoombox;
import se.metacraft.portable_jukebox.data.RemovalAware;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;

import java.util.*;
import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public abstract class EntityMixin implements HasBoombox {

	@Unique
	private final Set<BoomboxEntity> boomboxes = new HashSet<>();


	@Override
	public void portable_jukebox$addBoombox(BoomboxEntity entity) {
		this.boomboxes.add(entity);
	}

	@Override
	public void portable_jukebox$removeBoombox(BoomboxEntity entity) {
		boomboxes.remove(entity);
	}

	@Override
	public Set<BoomboxEntity> portable_jukebox$getBoomboxes() {
		return Collections.unmodifiableSet(boomboxes);
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if (this instanceof RemovalAware aware) {
			aware.onEntityRemoved(reason);
		}
	}
}
