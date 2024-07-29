package se.datasektionen.mc.better_pets.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.BetterPets;
import se.datasektionen.mc.better_pets.TameableExtension;
import se.datasektionen.mc.better_pets.TamedHelper;

import java.util.*;

@Mixin(TameableEntity.class)
public abstract class MixinTameableEntity extends AnimalEntity implements TameableExtension, Tameable {

	@Shadow public abstract @Nullable UUID getOwnerUuid();

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

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		Uuids.SET_CODEC.encodeStart(NbtOps.INSTANCE, trustedPlayers).resultOrPartial(
				BetterPets.LOGGER::error
		).ifPresent(players -> {
			nbt.put(TRUSTED_PLAYERS, players);
		});
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(TRUSTED_PLAYERS)) {
			Uuids.SET_CODEC.parse(NbtOps.INSTANCE, nbt.get(TRUSTED_PLAYERS)).resultOrPartial(
					BetterPets.LOGGER::error
			).ifPresent(players -> trustedPlayers = players);
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
	public ServerPlayerEntity metacraft$getCurrentFollowTarget() {
		if (!isTamed()) return null;
		if (followTargetOverride != null) {
			return followTargetOverride;
		} else {
			return getServer().getPlayerManager().getPlayer(getOwnerUuid());
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
}
