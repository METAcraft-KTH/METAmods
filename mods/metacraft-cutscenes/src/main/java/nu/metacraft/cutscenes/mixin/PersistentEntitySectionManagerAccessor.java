package nu.metacraft.cutscenes.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor<T extends EntityAccess> {

	@Accessor
	@Mutable
	void setVisibleEntityStorage(EntityLookup<T> index);

	@Accessor
	@Mutable
	void setSectionStorage(EntitySectionStorage<T> cache);

}
