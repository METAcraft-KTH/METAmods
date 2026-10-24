package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hoglin.setCannotBeHunted (saved as CannotBeHunted) is private; vanilla only calls it for bastion spawns. */
@Mixin(Hoglin.class)
public interface HoglinAccessor {
	@Invoker("setCannotBeHunted")
	void netherRifts$setCannotBeHunted(boolean cannotBeHunted);
}
