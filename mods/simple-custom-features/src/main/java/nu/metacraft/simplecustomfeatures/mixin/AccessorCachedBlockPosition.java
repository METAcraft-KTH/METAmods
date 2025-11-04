package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockInWorld.class)
public interface AccessorCachedBlockPosition {

	@Accessor
	void setState(BlockState state);

	@Accessor
	void setEntity(BlockEntity blockEntity);
	
	@Accessor
	void setCachedEntity(boolean cachedEntity);
}
