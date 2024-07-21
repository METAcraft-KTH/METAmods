package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.component.type.ContainerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import se.datasektionen.mc.metacraft_core.item.components.ExpiresComponent;

import java.util.List;

@Mixin(ContainerComponent.class)
public class MixinContainerComponent {

	@ModifyVariable(
			method = "fromSlots",
			at = @At("HEAD"),
			argsOnly = true
	)
	private static List<ContainerComponent.Slot> fixCodec(
			List<ContainerComponent.Slot> slots
	) {
		return slots.stream().filter(slot -> ExpiresComponent.applyDelete(slot.item()).isPresent()).toList();
	}

}
