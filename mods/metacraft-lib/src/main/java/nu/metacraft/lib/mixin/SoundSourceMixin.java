package nu.metacraft.lib.mixin;

import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SoundSource.class)
public abstract class SoundSourceMixin implements StringRepresentable {
	@Shadow public abstract String getName();

	@Override
	public String getSerializedName() {
		return getName();
	}
}
