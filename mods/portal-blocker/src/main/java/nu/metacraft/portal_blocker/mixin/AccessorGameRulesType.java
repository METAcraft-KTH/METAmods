package nu.metacraft.portal_blocker.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.BiConsumer;

@Mixin(GameRules.Type.class)
public interface AccessorGameRulesType<T> {

	@Accessor
	BiConsumer<MinecraftServer, T> getCallback();

	@Mutable
	@Accessor
	void setCallback(BiConsumer<MinecraftServer, T> callback);

}
