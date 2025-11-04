package nu.metacraft.simplecustomfeatures.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.core.IdMapper;

@Mixin(IdMapper.class)
public interface AccessorIdList<T> {

	@Accessor
	Reference2IntMap<T> getTToId();

	@Accessor
	List<T> getIdToT();

	@Accessor
	int getNextId();

	@Accessor
	void setNextId(int nextId);

}
