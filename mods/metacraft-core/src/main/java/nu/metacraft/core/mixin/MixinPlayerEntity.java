package nu.metacraft.core.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.gamerules.METAcraftGameRules;
import nu.metacraft.core.item.components.METAcraftComponents;

@Mixin(Player.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	@Shadow public abstract Inventory getInventory();

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(
		method = "dropEquipment",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/level/GameRules;RULE_KEEPINVENTORY:Lnet/minecraft/world/level/GameRules$Key;"
		)
	)
	public void dropInventory(CallbackInfo ci) {
		if (this.level().getServer().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
			for (int i = 0; i < getInventory().getContainerSize(); ++i) {
				ItemStack stack = getInventory().getItem(i);
				if (stack.has(METAcraftComponents.ANTI_KEEP_INVENTORY)) {
					if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
						this.drop(stack, true, false);
					}
					getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}

	@Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
	public void noBreakArmor(DamageSource source, float amount, CallbackInfo ci) {
		MinecraftServer server = level().getServer();
		if (server == null) {
			return;
		}
		if (!server.getGameRules().getRule(METAcraftGameRules.DO_ARMOR_DAMAGE).get()) {
			ci.cancel();
		}
	}
}
