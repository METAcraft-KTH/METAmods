package se.metacraft.bosses.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.util.TrackedEntity;
import se.metacraft.bosses.METAcraftBosses;
import se.metacraft.bosses.extensions.LivingEntityExtensions;
import se.metacraft.bosses.util.DoubleTeamHandler;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements LivingEntityExtensions {

	@Shadow public abstract void kill(ServerWorld world);

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

	public MixinLivingEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(
		method = "damage",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)V"
		),
		cancellable = true
	)
	public void damage(
			ServerWorld world, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (phantomEntity) {
			this.getWorld().sendEntityStatus(this, (byte) 60);
			discard();
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!getWorld().isClient()) {
			if (doubleTeamHandler != null) {
				doubleTeamHandler = doubleTeamHandler.tick();
				if (doubleTeamHandler.isDone()) {
					doubleTeamHandler = null;
				}
			}
			if (soulboundEntity != null) {
				soulboundEntity.tick();
				var target = soulboundEntity.getEntity(this.getServer());
				if (target.entityState() == TrackedEntity.EntityResult.EntityState.ABSENT || (target.isPresent() && !target.entity().isAlive())) {
					kill((ServerWorld) this.getWorld());
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
			var result = soulboundEntity.getEntity(getServer()).entity();
			if (result != null && result != this) {
				return ((LivingEntityExtensions) result).metacraft$getNonSoulboundMaster();
			} else {
				return this;
			}
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void save(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(PHANTOM_ENTITY, phantomEntity);
		final var ops = getRegistryManager().getOps(NbtOps.INSTANCE);
		if (doubleTeamHandler != null) {
			DoubleTeamHandler.getCodec((LivingEntity) (Object) this).encodeStart(
					ops, doubleTeamHandler
			).resultOrPartial(METAcraftBosses.LOGGER::error).ifPresent(
				data -> nbt.put(DOUBLE_TEAM_DATA, data)
			);
		}
		if (soulboundEntity != null) {
			TrackedEntity.ENTITY_CODEC.encodeStart(
					ops, soulboundEntity
			).resultOrPartial(METAcraftBosses.LOGGER::error).ifPresent(
					data -> nbt.put(SOULBOUND_ENTITY, data)
			);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void load(NbtCompound nbt, CallbackInfo ci) {
		phantomEntity = nbt.getBoolean(PHANTOM_ENTITY, false);
		final var ops = getRegistryManager().getOps(NbtOps.INSTANCE);
		if (nbt.contains(DOUBLE_TEAM_DATA)) {
			DoubleTeamHandler.getCodec(((LivingEntity) (Object) this)).parse(
					ops, nbt.get(DOUBLE_TEAM_DATA)
			).resultOrPartial(METAcraftBosses.LOGGER::error).ifPresent(
					handler -> this.doubleTeamHandler = handler
			);
		} else {
			doubleTeamHandler = null;
		}
		if (nbt.contains(SOULBOUND_ENTITY)) {
			TrackedEntity.ENTITY_CODEC.parse(
					ops, nbt.get(SOULBOUND_ENTITY)
			).resultOrPartial(METAcraftBosses.LOGGER::error).ifPresent(
					tracked -> this.soulboundEntity = tracked
			);
		} else {
			soulboundEntity = null;
		}
	}
}
