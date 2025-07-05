package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TeleportTarget.class)
public interface AccessorTeleportTarget {

	@Accessor
	@Mutable
	void setWorld(ServerWorld world);

}
