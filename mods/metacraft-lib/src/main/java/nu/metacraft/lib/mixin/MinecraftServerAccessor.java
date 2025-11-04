package nu.metacraft.lib.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

	@Accessor
	LevelStorageSource.LevelStorageAccess getStorageSource();

	@Accessor
	PlayerDataStorage getPlayerDataStorage();

}
