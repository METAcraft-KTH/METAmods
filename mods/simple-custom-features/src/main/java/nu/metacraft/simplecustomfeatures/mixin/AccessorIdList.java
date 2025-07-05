package nu.metacraft.simplecustomfeatures.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.util.collection.IdList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(IdList.class)
public interface AccessorIdList<T> {

	@Accessor
	Reference2IntMap<T> getIdMap();

	@Accessor
	List<T> getList();

	@Accessor
	int getNextId();

	@Accessor
	void setNextId(int nextId);

}
