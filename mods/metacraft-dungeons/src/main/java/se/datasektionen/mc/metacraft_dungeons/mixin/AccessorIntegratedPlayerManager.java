package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.integrated.IntegratedPlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedPlayerManager.class)
public interface AccessorIntegratedPlayerManager {

	@Accessor
	void setUserData(NbtCompound userData);
}
