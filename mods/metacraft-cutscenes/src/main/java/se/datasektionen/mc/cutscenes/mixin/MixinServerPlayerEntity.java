package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;
import se.datasektionen.mc.cutscenes.extension.ServerPlayerEntityExtensions;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow public ServerPlayNetworkHandler networkHandler;
	@Unique
	private CutsceneInstance cutscene;

	@Override
	public void metacraft_cutscenes$setCutscene(CutsceneInstance cutscene) {
		if (CutsceneHelper.isInMultiplayerCutscene((ServerPlayerEntity) (Object) this)) {
			return;
		}
		if (this.networkHandler == null) {
			TaskScheduler.scheduleImmediately(getServer(), () -> metacraft_cutscenes$setCutscene(cutscene));
			return;
		}
		if (this.cutscene != null && !this.cutscene.isEnded()) {
			this.cutscene.end();
			this.cutscene.removeCutscene(p -> {});
		}
		this.cutscene = cutscene;
		if (this.cutscene != null) {
			this.cutscene.setTargetWorld(this.getServerWorld());
			this.cutscene.addPlayer((ServerPlayerEntity) (Object) this);
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

	@WrapOperation(
		method = "dropItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public boolean dropItem(
			World world, Entity entity, Operation<Boolean> original
	) {
		var scene = CutsceneHelper.getCutscene((ServerPlayerEntity) (Object) this);
		if (scene.isPresent() && scene.get().getCutscene().resetPlayerData()) {
			scene.get().addEntity(CutsceneInstance.PLAYER_ITEM, entity);
			return true;
		}
		return original.call(world, entity);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void tick(CallbackInfo ci) {
		if (cutscene != null) {
			cutscene.tick();
			if (cutscene.isEnded()) {
				cutscene.removeCutscene(p -> {});
				cutscene = null;
			}
		}
	}

	@Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
	public void stopTeleportInMultiplayerCutscene(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (
				CutsceneHelper.isInMultiplayerCutscene((ServerPlayerEntity) (Object) this) &&
				!((EntityExtension) this).metacraft$canChangeWorldInCutscene() &&
				this.getWorld().getRegistryKey() != teleportTarget.world().getRegistryKey()
		) {
			cir.setReturnValue(this);
		}
	}

	@Inject(method = "teleportTo", at = @At("RETURN"))
	public void teleportPost(TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir) {
		if (cutscene != null && this.getWorld().getRegistryKey() != teleportTarget.world().getRegistryKey()) {
			cutscene.setTargetWorld(teleportTarget.world());
		}
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	public void onDisconnect(CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(getServer()).onPlayerLeave((ServerPlayerEntity) (Object) this);
		if (cutscene != null) {
			cutscene.close();
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(CutsceneInstance.CUTSCENE)) {
			CutsceneInstance.CODEC.parse(getRegistryManager().getOps(NbtOps.INSTANCE), nbt.get(CutsceneInstance.CUTSCENE)).resultOrPartial(
					Cutscenes.LOGGER::error
			).ifPresent(this::metacraft_cutscenes$setCutscene);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		if (cutscene != null) {
			CutsceneInstance.CODEC.encodeStart(getRegistryManager().getOps(NbtOps.INSTANCE), cutscene).resultOrPartial(
					Cutscenes.LOGGER::error
			).ifPresent(scene -> {
				nbt.put(CutsceneInstance.CUTSCENE, scene);
			});
		}
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.cutscene = ((MixinServerPlayerEntity) (Object) oldPlayer).cutscene;
		if (cutscene != null) {
			cutscene.removePlayer(oldPlayer);
			cutscene.addPlayer((ServerPlayerEntity) (Object) this);
		}
	}
}
