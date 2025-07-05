package nu.metacraft.core.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WitherEntity.class)
public interface AccessorWitherEntity {

	@Accessor
	ServerBossBar getBossBar();

}
