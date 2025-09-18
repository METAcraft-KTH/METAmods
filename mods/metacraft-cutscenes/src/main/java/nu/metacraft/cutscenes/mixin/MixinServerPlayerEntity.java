package nu.metacraft.cutscenes.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
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

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public ServerPlayNetworkHandler networkHandler;
	@Unique
	private CutsceneInstance cutscene;

	@Unique
	private boolean allowWrongMovements = false;

	public MixinServerPlayerEntity(World world, GameProfile profile) {
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
		if (CutsceneHelper.isInMultiplayerCutscene((ServerPlayerEntity) (Object) this)) {
			return;
		}
		if (this.networkHandler == null) {
			TaskScheduler.scheduleImmediately(getEntityWorld().getServer(), () -> metacraft_cutscenes$setCutscene(cutscene));
			return;
		}
		if (this.cutscene != null && !this.cutscene.isEnded()) {
			this.cutscene.end(false);
		}
		this.cutscene = cutscene;
		if (this.cutscene != null) {
			this.cutscene.addPlayer((ServerPlayerEntity) (Object) this);
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

	@ModifyVariable(method = "teleportTo", at = @At("HEAD"), argsOnly = true)
	public TeleportTarget fixTeleportToCutscene(TeleportTarget teleportTarget) {
		if (teleportTarget.world() instanceof CutsceneWorld cw) {
			((AccessorTeleportTarget) (Object) teleportTarget).setWorld(cw.getActualWorld());
		}
		return teleportTarget;
	}

	@Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
	public void stopTeleportInMultiplayerCutscene(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (
				CutsceneHelper.isInMultiplayerCutscene((ServerPlayerEntity) (Object) this) &&
				!((EntityExtension) this).metacraft$canChangeWorldInCutscene() &&
				this.getEntityWorld().getRegistryKey() != teleportTarget.world().getRegistryKey()
		) {
			cir.setReturnValue(this);
		}
	}

	@Inject(method = "teleportTo", at = @At("RETURN"))
	public void teleportPost(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (cutscene != null && this.getEntityWorld().getRegistryKey() != teleportTarget.world().getRegistryKey()) {
			cutscene.setTargetWorld(teleportTarget.world());
		}
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(getEntityWorld().getServer()).onPlayerLeave((ServerPlayerEntity) (Object) this);
		if (cutscene != null) {
			cutscene.close();
		}
	}

	@Inject(method = "readCustomData", at = @At("RETURN"))
	public void readNBT(ReadView nbt, CallbackInfo ci) {
		nbt.read(CutsceneInstance.CUTSCENE, CutsceneInstance.CODEC).ifPresent(scene -> {
			scene.finalizeParse(getEntityWorld().getServer());
			this.metacraft_cutscenes$setCutscene(scene);
		});
	}

	@Inject(method = "writeCustomData", at = @At("RETURN"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		if (cutscene != null) {
			nbt.put(CutsceneInstance.CUTSCENE, CutsceneInstance.CODEC, cutscene);
		}
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.cutscene = ((MixinServerPlayerEntity) (Object) oldPlayer).cutscene;
		if (cutscene != null) {
			cutscene.removePlayer(oldPlayer, false);
			cutscene.addPlayer((ServerPlayerEntity) (Object) this);
		}
	}
}
