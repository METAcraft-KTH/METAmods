package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TeleportTransition.class)
public interface AccessorTeleportTarget {

	@Accessor
	@Mutable
	void setNewLevel(ServerLevel world);

}
