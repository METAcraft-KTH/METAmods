package nu.metacraft.lib.mixin;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerModelPart.class)
public abstract class PlayerModelPartMixin implements StringRepresentable {
	@Shadow public abstract String getId();

	@Override
	public String getSerializedName() {
		return getId();
	}
}
