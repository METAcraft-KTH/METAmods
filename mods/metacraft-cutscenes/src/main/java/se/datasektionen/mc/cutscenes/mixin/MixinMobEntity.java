package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneWorld;

@Mixin(MobEntity.class)
public abstract class MixinMobEntity extends LivingEntity {

	protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@WrapOperation(
		method = "convertTo(Lnet/minecraft/entity/EntityType;Lnet/minecraft/entity/conversion/EntityConversionContext;Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/entity/conversion/EntityConversionContext$Finalizer;)Lnet/minecraft/entity/mob/MobEntity;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public boolean convertTo(ServerWorld world, Entity entity, Operation<Boolean> original) {
		if (world instanceof CutsceneWorld cutsceneWorld) {
			var id = cutsceneWorld.getCutscene().getIDForEntity(this);
			if (id.isPresent()) {
				cutsceneWorld.getCutscene().addEntity(id.get(), entity);
				return true;
			}
		}
		return original.call(world, entity);
	}

}
