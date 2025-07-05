package nu.metacraft.bosses.mixin;

import net.minecraft.entity.OminousItemSpawnerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OminousItemSpawnerEntity.class)
public interface AccessorOminousItemSpawnerEntity {

	@Invoker
	void callSetItem(ItemStack stack);

	@Accessor
	void setSpawnItemAfterTicks(long spawnItemAfterTicks);

	@Accessor("ITEM_NBT_KEY")
	static String getItemKey() {
		throw new IllegalStateException("MixinError");
	}

}
