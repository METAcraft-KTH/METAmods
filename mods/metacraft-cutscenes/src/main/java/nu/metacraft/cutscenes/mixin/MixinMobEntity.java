package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;

@Mixin(Mob.class)
public abstract class MixinMobEntity extends LivingEntity {

	protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@WrapOperation(
		method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	public boolean convertTo(ServerLevel world, Entity entity, Operation<Boolean> original) {
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
