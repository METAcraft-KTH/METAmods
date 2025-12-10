package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.util.helper.CustomNameHelper;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

	protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	public void getName(CallbackInfoReturnable<Component> cir) {
		if ((Object) this instanceof ServerPlayer p) {
			CustomNameHelper.getCustomName(p).ifPresent(name -> {
				cir.setReturnValue(Component.literal(name));
			});
		}
	}

	@ModifyExpressionValue(
			method = "decorateDisplayNameComponent",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"
			)
	)
	private String addTellClickEvent(String original) {
		if ((Object) this instanceof ServerPlayer p) {
			return CustomNameHelper.getCustomNameWithoutFormatting(p).orElse(original);
		}
		return original;
	}

}
