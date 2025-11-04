package nu.metacraft.bosses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.util.TrackedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.bosses.extensions.LivingEntityExtensions;
import nu.metacraft.bosses.util.DoubleTeamHandler;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements LivingEntityExtensions {

	@Shadow public abstract void kill(ServerLevel world);

	@Unique
	private static final String PHANTOM_ENTITY = "phantom_entity";

	@Unique
	private static final String DOUBLE_TEAM_DATA = "double_team_data";

	@Unique
	private static final String SOULBOUND_ENTITY = "soulbound_entity";


	@Unique
	private boolean phantomEntity = false;

	@Unique
	private TrackedEntity<Entity> soulboundEntity;

	@Unique
	private DoubleTeamHandler doubleTeamHandler = null;

	public MixinLivingEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Inject(
		method = "hurtServer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"
		),
		cancellable = true
	)
	public void damage(
			ServerLevel world, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (phantomEntity) {
			this.level().broadcastEntityEvent(this, (byte) 60);
			discard();
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!level().isClientSide()) {
			if (doubleTeamHandler != null) {
				doubleTeamHandler = doubleTeamHandler.tick();
				if (doubleTeamHandler.isDone()) {
					doubleTeamHandler = null;
				}
			}
			if (soulboundEntity != null) {
				soulboundEntity.tick();
				var target = soulboundEntity.getEntity(this.level().getServer());
				if (target.entityState() == TrackedEntity.EntityResult.EntityState.ABSENT || (target.isPresent() && !target.entity().isAlive())) {
					kill((ServerLevel) this.level());
				}
			}
		}
	}

	@Override
	public void metacraft$setPhantomEntity(boolean phantomEntity) {
		this.phantomEntity = phantomEntity;
	}

	@Override
	public void metacraft$setDoubleTeamHandler(DoubleTeamHandler handler) {
		this.doubleTeamHandler = handler;
	}

	@Override
	public void metacraft$setSoulboundEntity(Entity soulboundEntity) {
		this.soulboundEntity = soulboundEntity != null ? TrackedEntity.of(soulboundEntity) : null;
	}

	@Override
	public Entity metacraft$getNonSoulboundMaster() {
		if (soulboundEntity == null) {
			return this;
		} else {
			var result = soulboundEntity.getEntity(level().getServer()).entity();
			if (result != null && result != this) {
				return ((LivingEntityExtensions) result).metacraft$getNonSoulboundMaster();
			} else {
				return this;
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void save(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(PHANTOM_ENTITY, phantomEntity);
		nbt.storeNullable(
				DOUBLE_TEAM_DATA, DoubleTeamHandler.getCodec((LivingEntity) (Object) this),
				doubleTeamHandler
		);
		nbt.storeNullable(
				SOULBOUND_ENTITY, TrackedEntity.ENTITY_CODEC,
				soulboundEntity
		);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void load(ValueInput nbt, CallbackInfo ci) {
		phantomEntity = nbt.getBooleanOr(PHANTOM_ENTITY, false);
		doubleTeamHandler = nbt.read(
				DOUBLE_TEAM_DATA, DoubleTeamHandler.getCodec(((LivingEntity) (Object) this))
		).orElse(null);
		soulboundEntity = nbt.read(
				SOULBOUND_ENTITY, TrackedEntity.ENTITY_CODEC
		).orElse(null);
	}
}
