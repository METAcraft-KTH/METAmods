package se.metacraft.nether_rifts.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/** Mob.lootTable (saved as DeathLootTable) has no setter. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Mutable
	@Accessor("lootTable")
	void netherRifts$setLootTable(Optional<ResourceKey<LootTable>> table);
}
