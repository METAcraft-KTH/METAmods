package se.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.item.PolymerSpawnEggItem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import se.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.nether_rifts.NetherRifts;

/**
 * The rift mobs' own items, server-side (Polymer): the Grenadier's goggles and sword, and the mobs' spawn eggs. The
 * goggles and the sword are only ever the Grenadier's (see {@link #init} on the creative tabs).
 */
public final class RiftItems {

	private static final ResourceKey<Item> BOMBARDIER_HELMET_KEY = ResourceKey.create(Registries.ITEM, NetherRifts.getID("bombardier_helmet"));
	private static final ResourceKey<Item> GRENADIER_SWORD_KEY = ResourceKey.create(Registries.ITEM, NetherRifts.getID("grenadier_sword"));

	/**
	 * The Piglin Grenadier's goggles, on foot (the deck's Grenadier has his modelled). Only ever on its head: no recipe,
	 * and it never drops. Clients see paper with the item model {@code nether_rifts:bombardier_helmet} (from the mod's
	 * resource pack: the artist's goggles), which a piglin draws on its head by the model's {@code head} display.
	 */
	public static final Item BOMBARDIER_HELMET = Registry.register(BuiltInRegistries.ITEM, BOMBARDIER_HELMET_KEY,
			new SimplePolymerItem(new Item.Properties().setId(BOMBARDIER_HELMET_KEY).stacksTo(1), Items.PAPER, true));

	/**
	 * The Piglin Grenadier's sword, on foot: a golden sword built as vanilla's is ({@code Items.GOLDEN_SWORD}), that
	 * clients see in the artist's gloved hand ({@link GrenadierSword}). Only ever in its hand: no recipe, and it never
	 * drops.
	 */
	public static final Item GRENADIER_SWORD = Registry.register(BuiltInRegistries.ITEM, GRENADIER_SWORD_KEY,
			new GrenadierSword(new Item.Properties().setId(GRENADIER_SWORD_KEY).sword(ToolMaterial.GOLD, 3.0F, -2.4F)));

	/*
	 * Spawn eggs for the rift's own mobs: a spawned one belongs to no rift, so it's a plain mob. Vanilla clients have no
	 * creative tab for them (/give nether_rifts:war_drummer_spawn_egg); they see the vanilla egg of the closest mob.
	 */
	public static final Item WAR_DRUMMER_SPAWN_EGG = spawnEgg("war_drummer_spawn_egg", RiftEntities.WAR_DRUMMER, Items.PIGLIN_BRUTE_SPAWN_EGG);
	public static final Item WAR_GHAST_SPAWN_EGG = spawnEgg("war_ghast_spawn_egg", RiftEntities.WAR_GHAST, Items.GHAST_SPAWN_EGG);
	public static final Item GHAST_BOMBARDIER_SPAWN_EGG = spawnEgg("ghast_bombardier_spawn_egg", RiftEntities.GHAST_BOMBARDIER, Items.PIGLIN_SPAWN_EGG);
	public static final Item PIGLIN_GENERAL_SPAWN_EGG = spawnEgg("piglin_general_spawn_egg", RiftEntities.PIGLIN_GENERAL, Items.PIGLIN_BRUTE_SPAWN_EGG);

	private RiftItems() {}

	private static Item spawnEgg(String name, EntityType<?> type, Item look) {
		var key = ResourceKey.create(Registries.ITEM, NetherRifts.getID(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new PolymerSpawnEggItem(look, new Item.Properties().setId(key).spawnEgg(type)));
	}

	/**
	 * The namespace's items opt in to metacraft-core's creative tabs. That takes in the goggles and the sword too, which
	 * are the Grenadier's alone and should be in none: they wait on a way to leave items out.
	 */
	public static void init() {
		CreativeTabNamespaces.add(NetherRifts.NAMESPACE);
		// the Grenadier's kit, never handed out: in no tab
		CreativeTabNamespaces.hide(BOMBARDIER_HELMET);
		CreativeTabNamespaces.hide(GRENADIER_SWORD);
	}
}
