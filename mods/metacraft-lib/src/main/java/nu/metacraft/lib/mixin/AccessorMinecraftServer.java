package nu.metacraft.lib.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface AccessorMinecraftServer {

	@Accessor
	LevelStorage.Session getSession();

	@Accessor
	PlayerSaveHandler getSaveHandler();

}
