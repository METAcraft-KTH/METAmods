package nu.metacraft.booklet.mixin;

import eu.pb4.booklet.api.body.ImageBody;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.PlainMessage;
import nu.metacraft.booklet.Beside;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** An image under {@code beside/} with a description is laid out text-left, picture-right ({@link Beside}). */
@Mixin(value = ImageBody.class, remap = false)
public abstract class ImageBodyMixin {
	@Shadow @Final private Identifier identifier;
	@Shadow @Final private Optional<Component> description;

	@Inject(method = "asVanillaBody", at = @At("HEAD"), cancellable = true)
	private void metacraft$beside(PacketContext context, CallbackInfoReturnable<PlainMessage> cir) {
		if (Beside.wants(this.identifier) && this.description.isPresent()) {
			cir.setReturnValue(Beside.layout(this.identifier, this.description.get(), context));
		}
	}
}
