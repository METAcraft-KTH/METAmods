package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.StringIdentifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SoundCategory.class)
public abstract class MixinSoundCategory implements StringIdentifiable {
	@Shadow public abstract String getName();

	@Override
	public String asString() {
		return getName();
	}
}
