package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.item.PolymerSpawnEggItem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nu.metacraft.lib.CreativeTabNamespaces;
import nu.metacraft.nether_rifts.NetherRifts;

/** The rift mobs' own items, server-side (Polymer): the Bombardier's goggles and the mobs' spawn eggs. */
public final class RiftItems {

	private static final ResourceKey<Item> BOMBARDIER_HELMET_KEY = ResourceKey.create(Registries.ITEM, NetherRifts.getID("bombardier_helmet"));

	/**
	 * The Ghast Bombardier's goggles. Only ever on its head: in no creative tab, no recipe, and it
	 * never drops. Clients see paper with the item model {@code nether_rifts:bombardier_helmet} (from the mod's
	 * resource pack), which a piglin draws on its head by the model's {@code head} display.
	 */
	public static final Item BOMBARDIER_HELMET = Registry.register(BuiltInRegistries.ITEM, BOMBARDIER_HELMET_KEY,
			new SimplePolymerItem(new Item.Properties().setId(BOMBARDIER_HELMET_KEY).stacksTo(1), Items.PAPER, true));

	/*
	 * Spawn eggs for the rift's own mobs: a spawned one belongs to no rift, so it's a plain mob. Vanilla clients have no
	 * creative tab for them (/give nether_rifts:war_drummer_spawn_egg); they see the vanilla egg of the closest mob.
	 */
	public static final Item WAR_DRUMMER_SPAWN_EGG = spawnEgg("war_drummer_spawn_egg", RiftEntities.WAR_DRUMMER, Items.PIGLIN_BRUTE_SPAWN_EGG);
	public static final Item WAR_GHAST_SPAWN_EGG = spawnEgg("war_ghast_spawn_egg", RiftEntities.WAR_GHAST, Items.GHAST_SPAWN_EGG);
	public static final Item GHAST_BOMBARDIER_SPAWN_EGG = spawnEgg("ghast_bombardier_spawn_egg", RiftEntities.GHAST_BOMBARDIER, Items.PIGLIN_SPAWN_EGG);

	private RiftItems() {}

	private static Item spawnEgg(String name, EntityType<?> type, Item look) {
		var key = ResourceKey.create(Registries.ITEM, NetherRifts.getID(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new PolymerSpawnEggItem(look, new Item.Properties().setId(key).spawnEgg(type)));
	}

	public static void init() {
		CreativeTabNamespaces.add(NetherRifts.NAMESPACE);
	}
}
