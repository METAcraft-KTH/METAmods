package se.metacraft.nether_rifts.compat;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import se.metacraft.nether_rifts.mob.RiftEntities;
import se.metacraft.portable_jukebox.PortableJukeboxDatagen;

public class JukeboxCompat {

	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, holder) -> {
			if (
				key.identifier().equals(
					RiftEntities.WAR_DRUMMER.getDefaultLootTable().map(ResourceKey::identifier).orElse(null)
				)
			) {
				tableBuilder.withPool(
					LootPool.lootPool().setRolls(Holder.direct(new ConstantValue(1))).add(
						NestedLootTable.lootTableReference(holder.getOrThrow(
							PortableJukeboxDatagen.EntityLootTableGen.PIGLIN_JUKEBOX_DROP
						))
					)
				);
			}
		});
	}

}
