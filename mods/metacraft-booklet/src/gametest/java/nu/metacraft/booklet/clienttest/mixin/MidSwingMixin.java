package nu.metacraft.booklet.clienttest.mixin;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import nu.metacraft.booklet.clienttest.BookletRenders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the painter in {@link BookletRenders}' canvas picture mid-stroke: a swing lasts six ticks and
 * a render takes thirty frames, so a real one would be over before the picture is taken.
 * Test source set only; never ships.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class MidSwingMixin {
	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void metacraftBooklet$midSwing(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTicks, CallbackInfo ci) {
		if (BookletRenders.MID_SWING.contains(entity.getUUID())) {
			state.currentSwing = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, 6);
			state.swingAnimation = 0.3f;
		}
	}
}
