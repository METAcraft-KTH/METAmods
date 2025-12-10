package nu.metacraft.core.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WitherBoss.class)
public interface WitherBossAccessor {

	@Accessor
	ServerBossEvent getBossEvent();

}
