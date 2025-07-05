package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;
import nu.metacraft.lib.util.helper.TamedHelper;

import java.util.*;

@Mixin(TameableEntity.class)
public abstract class MixinTameableEntity extends AnimalEntity implements TameableExtension, Tameable {

	@Shadow public abstract boolean isTamed();

	@Unique
	private static final String TRUSTED_PLAYERS = "trusted_players";

	@Unique
	private ServerPlayerEntity followTargetOverride;

	@Unique
	private Set<UUID> trustedPlayers = new HashSet<>();

	protected MixinTameableEntity(EntityType<? extends AnimalEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "writeCustomData", at = @At("RETURN"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		nbt.put(TRUSTED_PLAYERS, Uuids.SET_CODEC, trustedPlayers);
	}

	@Inject(method = "readCustomData", at = @At("HEAD"))
	public void readNBT(ReadView nbt, CallbackInfo ci) {
		nbt.read(TRUSTED_PLAYERS, Uuids.SET_CODEC).ifPresent(
				players -> trustedPlayers = players
		);
		if (!getWorld().isClient()) {
			trustedPlayers.removeIf(
					id -> this.getServer().getUserCache().getByUuid(id).isEmpty()
			);
		}
	}

	@Inject(
			method = "canTarget",
			at = @At("HEAD"),
			cancellable = true
	)
	public void canTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		TamedHelper.getRelevantPlayer(target).filter(trustedPlayers::contains).ifPresent(p -> {
			cir.setReturnValue(false);
		});
	}

	@Override
	public void metacraft$tick() {
		if (followTargetOverride != null && followTargetOverride.isDisconnected()) {
			followTargetOverride = null;
		}
	}

	@Override
	public void metacraft$setCurrentFollowTarget(ServerPlayerEntity entity) {
		followTargetOverride = entity;
	}

	@Override
	public LivingEntity metacraft$getCurrentFollowTarget() {
		if (!isTamed()) return null;
		if (followTargetOverride != null) {
			if (followTargetOverride.getWorld() != getWorld()) {
				return null;
			}
			return followTargetOverride;
		} else {
			return getOwner();
		}
	}

	@Override
	public boolean metaraft$isTrusted(LivingEntity player) {
		return trustedPlayers.contains(player.getUuid());
	}

	@Override
	public void metacraft$addTrustedPlayer(UUID player) {
		trustedPlayers.add(player);
	}

	@Override
	public void metacraft$removeTrustedPlayer(UUID player) {
		trustedPlayers.remove(player);
	}

	@Override
	public Collection<UUID> metacraft$getTrustedPlayers() {
		return Collections.unmodifiableSet(trustedPlayers);
	}

	@ModifyExpressionValue(
			method = {
					"cannotFollowOwner",
					"shouldTryTeleportToOwner",
					"tryTeleportToOwner"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/passive/TameableEntity;getOwner()Lnet/minecraft/entity/LivingEntity;"
			)
	)
	public LivingEntity checkFollowTarget(LivingEntity original) {
		return metacraft$getCurrentFollowTarget();
	}
}
