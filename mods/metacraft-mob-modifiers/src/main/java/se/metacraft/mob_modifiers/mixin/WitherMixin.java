package se.metacraft.mob_modifiers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherBoss.class)
public class WitherMixin {

	@Unique
	private static final String DROP_NETHER_STAR = "metacraft:drop_nether_star";

	@Unique
	private boolean dropNetherStar = true;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(EntityType<?> type, Level level, CallbackInfo ci) {
		dropNetherStar = true;
	}

	@WrapOperation(
		method = "dropCustomDeathLoot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"
		)
	)
	public ItemEntity dropCustomLoot(
		WitherBoss instance, ServerLevel level, ItemLike itemLike, Operation<ItemEntity> original
	) {
		if (!dropNetherStar) return null;
		return original.call(instance, level, itemLike);
	}

	@Inject(
		method = "addAdditionalSaveData",
		at = @At("RETURN")
	)
	public void save(ValueOutput output, CallbackInfo ci) {
		output.putBoolean(DROP_NETHER_STAR, dropNetherStar);
	}

	@Inject(
		method = "readAdditionalSaveData",
		at = @At("RETURN")
	)
	public void load(ValueInput input, CallbackInfo ci) {
		dropNetherStar = input.getBooleanOr(DROP_NETHER_STAR, true);
	}

}
