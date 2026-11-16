package se.metacraft.ovvar.content.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The six enchantments only an ovve (or frack) takes. The definitions and their tags are data
 * ({@code data/ovvar/enchantment}, {@code data/minecraft/tags/enchantment}); this is their keys, names,
 * the config switch every behaviour reads its level through ({@link #level}), and the filter that keeps
 * a switched-off one from being offered ({@link #offered}, used by the enchanting table,
 * {@code enchant_with_levels} and {@code enchant_randomly} through the mixins).
 */
public final class OvveEnchantments {
	private OvveEnchantments() {}

	public static final ResourceKey<Enchantment> ENDER_POCKETS = key("ender_pockets");
	public static final ResourceKey<Enchantment> PATCHWORK_PROTECTION = key("patchwork_protection");
	public static final ResourceKey<Enchantment> MAGPIE = key("magpie");
	public static final ResourceKey<Enchantment> KEEPSAKE = key("keepsake");
	public static final ResourceKey<Enchantment> KEYRING = key("keyring");
	public static final ResourceKey<Enchantment> SPILLED_DRINK_CURSE = key("spilled_drink_curse");
	public static final List<ResourceKey<Enchantment>> ALL = List.of(ENDER_POCKETS, PATCHWORK_PROTECTION, MAGPIE, KEEPSAKE, KEYRING, SPILLED_DRINK_CURSE);

	/** Every ovve and frack (generated from the chapters), the six's {@code supported_items}. */
	public static final TagKey<Item> ENCHANTABLE_OVVE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "enchantable/ovve"));

	/** A player-facing name in both languages ({@code enchantment.ovvar.<id>}). */
	public record Names(String en, String sv) {}

	public static final Map<ResourceKey<Enchantment>, Names> NAMES;
	static {
		Map<ResourceKey<Enchantment>, Names> names = new LinkedHashMap<>();
		names.put(ENDER_POCKETS, new Names("Ender Pockets", "Enderfickor"));
		names.put(PATCHWORK_PROTECTION, new Names("Patchwork Protection", "Lappskydd"));
		names.put(MAGPIE, new Names("Magpie", "Skata"));
		names.put(KEEPSAKE, new Names("Keepsake", "Arvegods"));
		names.put(KEYRING, new Names("Keyring", "Nyckelring"));
		names.put(SPILLED_DRINK_CURSE, new Names("Curse of the Spilled Drink", "Spillets förbannelse"));
		NAMES = Collections.unmodifiableMap(names);
	}

	/** Is Brewery here? Read once: the curse is off without it, the same rule as kultur's drinks. */
	public static final boolean BREWERY = FabricLoader.getInstance().isModLoaded("brewery");
	/** The built-in datapack that puts the curse in {@code #minecraft:on_random_loot}, registered only with Brewery. */
	public static final Identifier BREWERY_PACK = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "brewery_curse");

	private static ResourceKey<Enchantment> key(String id) {
		return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, id));
	}

	public static void init() {
		if (BREWERY) {
			var self = FabricLoader.getInstance().getModContainer(Ovvar.MOD_ID).orElseThrow();
			boolean ok = ResourceLoader.registerBuiltinPack(BREWERY_PACK, self, Component.literal("Ovvar: Curse of the Spilled Drink (with Brewery)"),
					PackActivationType.ALWAYS_ENABLED);
			if (!ok) Ovvar.LOGGER.warn("[{}] the brewery_curse pack could not be registered", Ovvar.MOD_ID);
		}
		EnderPockets.init();
		Keepsake.init();
		SpilledDrink.init();
		Keyring.init();
		// Each enchantment's behaviour hooks in here.
	}

	// ---- levels

	/** The raw level of {@code key} on any stack, config or not; 0 when absent. */
	public static int levelOn(ItemStack stack, ResourceKey<Enchantment> key) {
		if (stack.isEmpty()) return 0;
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(key)) return entry.getIntValue();
		}
		return 0;
	}

	/** The level a behaviour acts on: 0 unless the stack is an ovve or frack and the enchantment is switched on. */
	public static int level(ItemStack garment, ResourceKey<Enchantment> key) {
		if (!(garment.getItem() instanceof OvveItem) || !enabled(key)) return 0;
		return levelOn(garment, key);
	}

	/** The level on the garment this entity wears (see {@link OvveItem#worn}). */
	public static int level(LivingEntity wearer, ResourceKey<Enchantment> key) {
		return wearer == null ? 0 : level(OvveItem.worn(wearer), key);
	}

	// ---- switches

	public static boolean enabled(ResourceKey<Enchantment> key) {
		return enabled(key, OvvarConfig.get().enchantments(), BREWERY);
	}

	/** Is this one of the six switched on? Anything else: true. The curse also needs Brewery. */
	public static boolean enabled(ResourceKey<Enchantment> key, EnchantmentsConfig cfg, boolean brewery) {
		if (ENDER_POCKETS.equals(key)) return cfg.enderPockets().enabled();
		if (PATCHWORK_PROTECTION.equals(key)) return cfg.patchworkProtection().enabled();
		if (MAGPIE.equals(key)) return cfg.magpie().enabled();
		if (KEEPSAKE.equals(key)) return cfg.keepsake().enabled();
		if (KEYRING.equals(key)) return cfg.keyring().enabled();
		if (SPILLED_DRINK_CURSE.equals(key)) return brewery && cfg.spilledDrink().enabled();
		return true;
	}

	public static boolean offered(Holder<Enchantment> holder) {
		return offered(holder, OvvarConfig.get().enchantments(), BREWERY);
	}

	/** May the table, a loot function or a trade hand this out? False only for one of ours that is switched off. */
	public static boolean offered(Holder<Enchantment> holder, EnchantmentsConfig cfg, boolean brewery) {
		for (ResourceKey<Enchantment> own : ALL) {
			if (holder.is(own)) return enabled(own, cfg, brewery);
		}
		return true;
	}
}
