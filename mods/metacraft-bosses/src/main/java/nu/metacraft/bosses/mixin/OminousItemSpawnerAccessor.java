package nu.metacraft.bosses.mixin;

import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OminousItemSpawner.class)
public interface OminousItemSpawnerAccessor {

	@Invoker
	void callSetItem(ItemStack stack);

	@Accessor
	void setSpawnItemAfterTicks(long spawnItemAfterTicks);

	@Accessor("TAG_ITEM")
	static String getItemKey() {
		throw new IllegalStateException("MixinError");
	}

}
