package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.StringIdentifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerModelPart.class)
public abstract class MixinPlayerModelPart implements StringIdentifiable {
	@Shadow public abstract String getName();

	@Override
	public String asString() {
		return getName();
	}
}
