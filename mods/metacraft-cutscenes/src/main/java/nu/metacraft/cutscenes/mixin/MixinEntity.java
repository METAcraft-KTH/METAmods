package nu.metacraft.cutscenes.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.extension.EntityExtension;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtension {

	@Unique
	private static final String HAS_ACCURATE_MOVEMENT = "metacraft:has_accurate_movement";

	@Shadow private Level level;

	@Shadow public abstract @Nullable Entity teleport(TeleportTransition teleportTarget);

	@Unique
	private boolean canChangeWorldInCutscene = false;

	@Unique
	private boolean hasAccurateMovement = false;

	@Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
	public void stopTeleportInMultiplayerCutscene(TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (
				this.level instanceof CutsceneWorld && !canChangeWorldInCutscene &&
				this.level.dimension() != teleportTarget.newLevel().dimension()
		) {
			cir.setReturnValue((Entity) (Object) this);
		}
	}

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	public void save(ValueOutput nbt, CallbackInfo ci) {
		if (hasAccurateMovement) {
			nbt.putBoolean(HAS_ACCURATE_MOVEMENT, true);
		}
	}

	@Inject(method = "load", at = @At("RETURN"))
	public void load(ValueInput nbt, CallbackInfo ci) {
		hasAccurateMovement = nbt.getBooleanOr(HAS_ACCURATE_MOVEMENT, false);
	}


	@Override
	public Entity metacraft$teleportInCutscene(TeleportTransition target) {
		canChangeWorldInCutscene = true;
		var result = this.teleport(target);
		canChangeWorldInCutscene = false;
		return result;
	}

	@Override
	public boolean metacraft$canChangeWorldInCutscene() {
		return canChangeWorldInCutscene;
	}



	@Override
	public void metacraft$setHasAccurateMovement(boolean accurateMovement) {
		this.hasAccurateMovement = accurateMovement;
	}

	@Override
	public boolean metacraft$hasAccurateMovement() {
		return hasAccurateMovement;
	}
}
