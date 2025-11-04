package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nu.metacraft.core.item.components.ExpiresComponent;

import java.util.List;
import net.minecraft.world.item.component.ItemContainerContents;

@Mixin(ItemContainerContents.class)
public class MixinContainerComponent {

	@ModifyVariable(
			method = "fromSlots",
			at = @At("HEAD"),
			argsOnly = true
	)
	private static List<ItemContainerContents.Slot> fixCodec(
			List<ItemContainerContents.Slot> slots
	) {
		return slots.stream().filter(slot -> ExpiresComponent.applyDelete(slot.item()).isPresent()).toList();
	}

}
