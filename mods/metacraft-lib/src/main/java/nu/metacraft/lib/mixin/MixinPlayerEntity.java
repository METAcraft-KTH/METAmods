package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.util.helper.CustomNameHelper;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	public void getName(CallbackInfoReturnable<Text> cir) {
		if ((Object) this instanceof ServerPlayerEntity p) {
			CustomNameHelper.getCustomName(p).ifPresent(name -> {
				cir.setReturnValue(Text.literal(name));
			});
		}
	}

	@ModifyExpressionValue(
			method = "addTellClickEvent",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"
			)
	)
	private String addTellClickEvent(String original) {
		if ((Object) this instanceof ServerPlayerEntity p) {
			return CustomNameHelper.getCustomNameWithoutFormatting(p).orElse(original);
		}
		return original;
	}

}
