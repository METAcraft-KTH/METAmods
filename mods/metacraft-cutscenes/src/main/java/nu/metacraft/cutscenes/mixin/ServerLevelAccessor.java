package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
	@Invoker
	LevelEntityGetter<Entity> callGetEntities();

	@Accessor
	@Mutable
	void setEntityManager(PersistentEntitySectionManager<Entity> manager);

	@Accessor
	@Mutable
	void setChunkSource(ServerChunkCache chunkManager);

}
