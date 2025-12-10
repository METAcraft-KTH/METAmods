package nu.metacraft.zones.mixin;

import com.mojang.brigadier.context.StringRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StringRange.class, remap = false)
public interface StringRangeAccessor {

	@Mutable
	@Accessor
	void setStart(int start);
	@Mutable
	@Accessor
	void setEnd(int end);

}
