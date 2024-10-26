package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootContextTypes.class)
public interface AccessorLootContextTypes {

	@Accessor("MAP")
	static BiMap<Identifier, ContextType> getMap() {
		return null;
	}

}
