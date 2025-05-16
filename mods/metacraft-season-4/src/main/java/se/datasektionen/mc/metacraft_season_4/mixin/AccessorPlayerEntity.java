package se.datasektionen.mc.metacraft_season_4.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerEntity.class)
public interface AccessorPlayerEntity {

	@Invoker
	void callVanishCursedItems();

	@Invoker
	float callGetDamageAgainst(Entity target, float baseDamage, DamageSource damageSource);

}
