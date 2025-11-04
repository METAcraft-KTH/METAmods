package nu.metacraft.cutscenes.mixin;

import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.extension.EntityExtension;
import nu.metacraft.cutscenes.extension.ServerPlayerEntityExtensions;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerEntity extends Player implements ServerPlayerEntityExtensions {

	@Shadow public ServerGamePacketListenerImpl connection;
	@Unique
	private CutsceneInstance cutscene;

	@Unique
	private boolean allowWrongMovements = false;

	public MixinServerPlayerEntity(Level world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public void metacraft$setAllowWrongMovements(boolean allowWrongMovements) {
		this.allowWrongMovements = allowWrongMovements;
	}

	@Override
	public boolean metacraft$getAllowWrongMovements() {
		return allowWrongMovements;
	}

	@Override
	public void metacraft_cutscenes$setCutscene(CutsceneInstance cutscene) {
		if (CutsceneHelper.isInMultiplayerCutscene((ServerPlayer) (Object) this)) {
			return;
		}
		if (this.connection == null) {
			TaskScheduler.scheduleImmediately(level().getServer(), () -> metacraft_cutscenes$setCutscene(cutscene));
			return;
		}
		if (this.cutscene != null && !this.cutscene.isEnded()) {
			this.cutscene.end(false);
		}
		this.cutscene = cutscene;
		if (this.cutscene != null) {
			this.cutscene.addPlayer((ServerPlayer) (Object) this);
			this.cutscene.setRemoveHandler(new CutsceneInstance.RemoveHandler() {

				@Override
				public void beforePlayerReset(CutsceneInstance cutscene) {

				}

				@Override
				public void afterPlayerReset(CutsceneInstance cutscene) {
					cutscene.createNextCutscene().ifPresent(next -> {
						metacraft_cutscenes$setCutscene(next);
					});
				}
			});
		}
	}

	@Override
	public Optional<CutsceneInstance> metacraft_cutscenes$getCutscene() {
		return Optional.ofNullable(cutscene);
	}

	@Override
	public boolean metacraft_cutscenes$hasCutscene() {
		return cutscene != null;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void tick(CallbackInfo ci) {
		if (cutscene != null) {
			cutscene.tick();
			if (cutscene.isEnded()) {
				cutscene = null;
			}
		}
	}

	@ModifyVariable(method = "teleport", at = @At("HEAD"), argsOnly = true)
	public TeleportTransition fixTeleportToCutscene(TeleportTransition teleportTarget) {
		if (teleportTarget.newLevel() instanceof CutsceneWorld cw) {
			((AccessorTeleportTarget) (Object) teleportTarget).setNewLevel(cw.getActualWorld());
		}
		return teleportTarget;
	}

	@Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
	public void stopTeleportInMultiplayerCutscene(TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (
				CutsceneHelper.isInMultiplayerCutscene((ServerPlayer) (Object) this) &&
				!((EntityExtension) this).metacraft$canChangeWorldInCutscene() &&
				this.level().dimension() != teleportTarget.newLevel().dimension()
		) {
			cir.setReturnValue(this);
		}
	}

	@Inject(method = "teleport", at = @At("RETURN"))
	public void teleportPost(TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (cutscene != null && this.level().dimension() != teleportTarget.newLevel().dimension()) {
			cutscene.setTargetWorld(teleportTarget.newLevel());
		}
	}

	@Inject(method = "disconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(level().getServer()).onPlayerLeave((ServerPlayer) (Object) this);
		if (cutscene != null) {
			cutscene.close();
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		nbt.read(CutsceneInstance.CUTSCENE, CutsceneInstance.CODEC).ifPresent(scene -> {
			scene.finalizeParse(level().getServer());
			this.metacraft_cutscenes$setCutscene(scene);
		});
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		if (cutscene != null) {
			nbt.store(CutsceneInstance.CUTSCENE, CutsceneInstance.CODEC, cutscene);
		}
	}

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		this.cutscene = ((MixinServerPlayerEntity) (Object) oldPlayer).cutscene;
		if (cutscene != null) {
			cutscene.removePlayer(oldPlayer, false);
			cutscene.addPlayer((ServerPlayer) (Object) this);
		}
	}
}
