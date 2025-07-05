package nu.metacraft.lib.mixin;

import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelProperties.class)
public interface AccessorLevelProperties {

	@Accessor
	void setLevelInfo(LevelInfo info);

}
