package se.datasektionen.mc.zones.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import xyz.nucleoid.leukocyte.authority.Authority;
import xyz.nucleoid.leukocyte.authority.IndexedAuthorityMap;

@Mixin(value = IndexedAuthorityMap.class, remap = false)
public interface AccessorIndexedAuthorityMap {

	@Invoker
	void callAddToDimension(Authority authority);

	@Invoker
	void callRemoveFromDimension(String key);

}
