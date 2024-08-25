package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneWorld;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtension {

	@Shadow private World world;

	@Shadow public abstract @Nullable Entity teleportTo(TeleportTarget teleportTarget);

	@Unique
	private boolean canChangeWorldInCutscene = false;

	@Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
	public void stopTeleportInMultiplayerCutscene(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (
				this.world instanceof CutsceneWorld && !canChangeWorldInCutscene &&
				this.world.getRegistryKey() != teleportTarget.world().getRegistryKey()
		) {
			cir.setReturnValue((Entity) (Object) this);
		}
	}

	@Override
	public Entity metacraft$teleportInCutscene(TeleportTarget target) {
		canChangeWorldInCutscene = true;
		var result = this.teleportTo(target);
		canChangeWorldInCutscene = false;
		return result;
	}

	@Override
	public boolean metacraft$canChangeWorldInCutscene() {
		return canChangeWorldInCutscene;
	}
}
