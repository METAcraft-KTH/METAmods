package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityIndex;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerEntityManager.class)
public interface AccesorServerEntityManager<T extends EntityLike> {

	@Accessor
	@Mutable
	void setIndex(EntityIndex<T> index);

	@Accessor
	@Mutable
	void setCache(SectionedEntityCache<T> cache);

}
