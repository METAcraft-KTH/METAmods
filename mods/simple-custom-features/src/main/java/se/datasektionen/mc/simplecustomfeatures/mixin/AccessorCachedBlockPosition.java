package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.pattern.CachedBlockPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CachedBlockPosition.class)
public interface AccessorCachedBlockPosition {

	@Accessor
	void setState(BlockState state);

	@Accessor
	void setBlockEntity(BlockEntity blockEntity);
	
	@Accessor
	void setCachedEntity(boolean cachedEntity);
}
