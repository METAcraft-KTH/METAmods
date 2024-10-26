package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.gamerules.METAcraftGameRules;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	@Shadow public abstract PlayerInventory getInventory();

	@Shadow public abstract ItemEntity dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership);

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "remove", at = @At("RETURN"))
	public void remove(RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity p && !reason.shouldDestroy()) {
			var point = ((ServerPlayerEntityExtensions) p).metacraft_lib$getMusicPoint();
			if (point != null) {
				point.discard();
			}
		}
	}

	@Inject(
		method = "dropInventory",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/GameRules;KEEP_INVENTORY:Lnet/minecraft/world/GameRules$Key;"
		)
	)
	public void dropInventory(CallbackInfo ci) {
		if (this.getServer().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			for (int i = 0; i < getInventory().size(); ++i) {
				ItemStack stack = getInventory().getStack(i);
				if (stack.contains(METAcraftComponents.ANTI_KEEP_INVENTORY)) {
					this.dropItem(stack, true, false);
					getInventory().setStack(i, ItemStack.EMPTY);
				}
			}
		}
	}

	@Inject(method = "damageArmor", at = @At("HEAD"), cancellable = true)
	public void noBreakArmor(DamageSource source, float amount, CallbackInfo ci) {
		MinecraftServer server = getServer();
		if (server == null) {
			return;
		}
		if (!server.getGameRules().get(METAcraftGameRules.DO_ARMOR_DAMAGE).get()) {
			ci.cancel();
		}
	}
}
