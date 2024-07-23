package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public interface AccessorMinecraftServer {

	@Accessor
	Map<RegistryKey<World>, ServerWorld> getWorlds();

	@Accessor
	Executor getWorkerExecutor();

	@Accessor
	LevelStorage.Session getSession();

	@Accessor
	PlayerSaveHandler getSaveHandler();

}
