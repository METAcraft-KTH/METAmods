package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@Mixin(TamableAnimal.class)
public abstract class MixinTameableEntity extends Animal implements TameableExtension, OwnableEntity {

	@Shadow public abstract boolean isTame();

	@Unique
	private static final String TRUSTED_PLAYERS = "trusted_players";

	@Unique
	private ServerPlayer followTargetOverride;

	@Unique
	private Set<UUID> trustedPlayers = new HashSet<>();

	protected MixinTameableEntity(EntityType<? extends Animal> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.store(TRUSTED_PLAYERS, UUIDUtil.CODEC_SET, trustedPlayers);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		nbt.read(TRUSTED_PLAYERS, UUIDUtil.CODEC_SET).ifPresent(
				players -> trustedPlayers = players
		);
		if (!level().isClientSide()) {
			trustedPlayers.removeIf(
					id -> this.level().getServer().services().nameToIdCache().get(id).isEmpty()
			);
		}
	}

	@Inject(
			method = "canAttack",
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
		if (followTargetOverride != null && followTargetOverride.hasDisconnected()) {
			followTargetOverride = null;
		}
	}

	@Override
	public void metacraft$setCurrentFollowTarget(ServerPlayer entity) {
		followTargetOverride = entity;
	}

	@Override
	public LivingEntity metacraft$getCurrentFollowTarget() {
		if (!isTame()) return null;
		if (followTargetOverride != null) {
			if (followTargetOverride.level() != level()) {
				return null;
			}
			return followTargetOverride;
		} else {
			return getOwner();
		}
	}

	@Override
	public boolean metaraft$isTrusted(LivingEntity player) {
		return trustedPlayers.contains(player.getUUID());
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
					"unableToMoveToOwner",
					"shouldTryTeleportToOwner",
					"tryToTeleportToOwner"
			},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/TamableAnimal;getOwner()Lnet/minecraft/world/entity/LivingEntity;"
			)
	)
	public LivingEntity checkFollowTarget(LivingEntity original) {
		return metacraft$getCurrentFollowTarget();
	}
}
