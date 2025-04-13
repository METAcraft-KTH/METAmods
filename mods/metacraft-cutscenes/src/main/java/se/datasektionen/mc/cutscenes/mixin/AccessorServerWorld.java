package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerWorld.class)
public interface AccessorServerWorld {
	@Invoker
	EntityLookup<Entity> callGetEntityLookup();

	@Accessor
	@Mutable
	void setEntityManager(ServerEntityManager<Entity> manager);

	@Accessor
	@Mutable
	void setChunkManager(ServerChunkManager chunkManager);

}
