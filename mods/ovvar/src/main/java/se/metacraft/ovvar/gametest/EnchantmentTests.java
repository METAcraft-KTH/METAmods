package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityTypes;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.MobOvvar;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.enchantment.Keepsake;
import se.metacraft.ovvar.content.enchantment.SpilledDrink;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import se.metacraft.ovvar.content.enchantment.Magpie;
import se.metacraft.ovvar.content.item.PatchItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.enchantment.PatchworkProtection;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.item.OvveFeet;
import se.metacraft.ovvar.content.item.OvveItem;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static se.metacraft.ovvar.content.enchantment.OvveEnchantments.*;

/** The six ovve enchantments: what takes them, what they clash with, where they come from, and the glint. */
public final class EnchantmentTests {
	private static HolderLookup.RegistryLookup<Enchantment> registry(GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return registry(helper).getOrThrow(key);
	}

	@GameTest
	public void everyOvveTakesTheSix(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
			if (!ovve.is(ENCHANTABLE_OVVE)) wrong.add(chapter + " not in #ovvar:enchantable/ovve");
			if (!ovve.isEnchantable()) wrong.add(chapter + " not enchantable");
			for (ResourceKey<Enchantment> key : ALL) {
				if (!holder(helper, key).value().canEnchant(ovve)) wrong.add(chapter + " refuses " + key.identifier());
			}
		}
		List<ItemStack> others = List.of(new ItemStack(ModContent.top(Chapter.DATA)),
				new ItemStack(ModContent.patchItem(Patches.all().getFirst())), new ItemStack(Items.DIAMOND_LEGGINGS));
		for (ItemStack other : others) {
			for (ResourceKey<Enchantment> key : ALL) {
				if (holder(helper, key).value().canEnchant(other)) wrong.add(other.getItem() + " takes " + key.identifier());
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** No vanilla (or other mod's) enchantment goes on an ovve: not Unbreaking, not Mending, not any Protection. */
	@GameTest
	public void unbreakingAndMendingNeverApply(GameTestHelper helper) {
		// Fails loudly before the six exist, like the rest of this class.
		holder(helper, MAGPIE);
		List<String> wrong = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
			registry(helper).listElements().forEach(h -> {
				if (h.key().identifier().getNamespace().equals("ovvar")) return;
				if (h.value().canEnchant(ovve)) wrong.add(chapter + " takes " + h.key().identifier());
			});
		}
		if (!wrong.isEmpty()) {
			helper.fail("vanilla enchantments apply to an ovve (" + Enchantments.UNBREAKING.identifier() + " and "
					+ Enchantments.MENDING.identifier() + " must never): " + wrong);
		}
		helper.succeed();
	}

	@GameTest
	public void incompatibilities(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		if (Enchantment.areCompatible(holder(helper, ENDER_POCKETS), holder(helper, KEYRING))) wrong.add("ender pockets + keyring compatible");
		for (ResourceKey<Enchantment> protection : List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION,
				Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
			if (Enchantment.areCompatible(holder(helper, PATCHWORK_PROTECTION), holder(helper, protection))) {
				wrong.add("patchwork + " + protection.identifier() + " compatible");
			}
		}
		for (int i = 0; i < ALL.size(); i++) {
			for (int j = i + 1; j < ALL.size(); j++) {
				ResourceKey<Enchantment> a = ALL.get(i), b = ALL.get(j);
				boolean pockets = Set.of(a, b).equals(Set.of(ENDER_POCKETS, KEYRING));
				if (!pockets && !Enchantment.areCompatible(holder(helper, a), holder(helper, b))) {
					wrong.add(a.identifier() + " + " + b.identifier() + " incompatible");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void tableOffersFiveNeverTheCurse(GameTestHelper helper) {
		var registry = registry(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		Set<ResourceKey<Enchantment>> seen = new HashSet<>();
		for (int seed = 0; seed < 500; seed++) {
			for (EnchantmentInstance instance : EnchantmentHelper.selectEnchantment(RandomSource.create(seed), ovve, 30,
					registry.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream())) {
				seen.add(instance.enchantment().unwrapKey().orElseThrow());
			}
		}
		Set<ResourceKey<Enchantment>> want = new HashSet<>(ALL);
		want.remove(SPILLED_DRINK_CURSE);
		helper.assertValueEqual(seen, want, "what the table offers an ovve at level 30");
		Holder<Enchantment> curse = holder(helper, SPILLED_DRINK_CURSE);
		helper.assertTrue(curse.is(EnchantmentTags.CURSE), "the curse is in #minecraft:curse");
		helper.assertTrue(curse.is(EnchantmentTags.TREASURE), "the curse is in #minecraft:treasure");
		helper.assertFalse(curse.is(EnchantmentTags.IN_ENCHANTING_TABLE), "the curse is in #minecraft:in_enchanting_table");
		helper.assertFalse(curse.is(EnchantmentTags.TRADEABLE), "the curse is in #minecraft:tradeable");
		helper.succeed();
	}

	@GameTest
	public void randomLootBooksFollowBrewery(GameTestHelper helper) {
		boolean brewery = FabricLoader.getInstance().isModLoaded("brewery");
		for (ResourceKey<Enchantment> key : ALL) {
			boolean want = key != SPILLED_DRINK_CURSE || brewery;
			helper.assertValueEqual(holder(helper, key).is(EnchantmentTags.ON_RANDOM_LOOT), want, key.identifier() + " in #minecraft:on_random_loot");
		}
		LootItemFunction function = EnchantRandomlyFunction.randomEnchantment().build();
		LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
		Set<ResourceKey<Enchantment>> seen = new HashSet<>();
		for (int seed = 0; seed < 4000; seed++) {
			LootContext context = new LootContext.Builder(params).withOptionalRandomSource(RandomSource.create(seed)).create(Optional.empty());
			ItemStack book = function.apply(new ItemStack(Items.BOOK), context);
			for (Holder<Enchantment> h : EnchantmentHelper.getEnchantmentsForCrafting(book).keySet()) h.unwrapKey().ifPresent(seen::add);
		}
		for (ResourceKey<Enchantment> key : ALL) {
			boolean want = key != SPILLED_DRINK_CURSE || brewery;
			helper.assertValueEqual(seen.contains(key), want, key.identifier() + " on a random book (brewery " + brewery + ")");
		}
		helper.assertValueEqual(OvveEnchantments.BREWERY, brewery, "OvveEnchantments.BREWERY");
		var selected = helper.getLevel().getServer().getPackRepository().getSelectedIds();
		helper.assertValueEqual(selected.contains("ovvar:brewery_curse"), brewery, "ovvar:brewery_curse selected (" + selected + ")");
		helper.succeed();
	}

	@GameTest
	public void curseOnlyWithBrewery(GameTestHelper helper) {
		Holder<Enchantment> curse = holder(helper, SPILLED_DRINK_CURSE), magpie = holder(helper, MAGPIE);
		EnchantmentsConfig cfg = EnchantmentsConfig.DEFAULT;
		helper.assertFalse(offered(curse, cfg, false), "the curse offered without Brewery");
		helper.assertTrue(offered(curse, cfg, true), "the curse offered with Brewery");
		helper.assertFalse(enabled(SPILLED_DRINK_CURSE, cfg, false), "the curse enabled without Brewery");
		helper.assertTrue(offered(magpie, cfg, false), "magpie offered without Brewery");
		helper.succeed();
	}

	@GameTest
	public void disabledIsNotOffered(GameTestHelper helper) {
		EnchantmentsConfig cfg = EnchantmentsConfig.DEFAULT.keyring(new EnchantmentsConfig.Keyring(false, 20));
		helper.assertFalse(offered(holder(helper, KEYRING), cfg, BREWERY), "keyring offered while off");
		helper.assertTrue(offered(holder(helper, ENDER_POCKETS), cfg, BREWERY), "ender pockets offered");
		helper.assertTrue(offered(holder(helper, Enchantments.PROTECTION), cfg, BREWERY), "protection offered");
		helper.assertFalse(enabled(KEYRING, cfg, BREWERY), "keyring enabled while off");
		helper.succeed();
	}

	/**
	 * What a vanilla client reads on an ovve with Ender Pockets and the curse: Polymer sends the server's tooltip as lore,
	 * each line wrapped in a white, non-italic parent, so the colour that shows is the one on the enchantment's own
	 * text inside it: red for the curse, gray for Ender Pockets, as vanilla draws them.
	 */
	@GameTest
	public void theCurseIsRedAndEnderPocketsGrayOnTheClientStack(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(holder(helper, ENDER_POCKETS), 1);
		ovve.enchant(holder(helper, SPILLED_DRINK_CURSE), 1);
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		List<Component> lore = client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
		List<String> wrong = new ArrayList<>();
		checkShownColour(lore, "Curse of the Spilled Drink", ChatFormatting.RED, wrong);
		checkShownColour(lore, "Ender Pockets", ChatFormatting.GRAY, wrong);
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** The colour each piece of a lore line's text is drawn in, parents' styles applied, as the client resolves it. */
	private static void checkShownColour(List<Component> lore, String name, ChatFormatting want, List<String> wrong) {
		Optional<Component> line = lore.stream().filter(l -> l.getString().contains(name)).findFirst();
		if (line.isEmpty()) {
			wrong.add("no lore line names " + name + ": " + lore.stream().map(Component::getString).toList());
			return;
		}
		Set<TextColor> shown = new HashSet<>();
		line.get().visit((style, text) -> {
			if (!text.isBlank()) shown.add(style.getColor());
			return Optional.empty();
		}, Style.EMPTY);
		if (!shown.equals(Set.of(TextColor.fromLegacyFormat(want)))) wrong.add(name + " shows in " + shown + ", wanted " + want);
	}

	@GameTest
	public void enchantedOvveGlintsAndSoDoesItsTop(GameTestHelper helper) {
		Holder<Enchantment> magpie = holder(helper, MAGPIE);
		List<String> wrong = new ArrayList<>();
		for (boolean enchanted : List.of(true, false)) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
			OvveItem.setTopUp(ovve, true);
			if (enchanted) ovve.enchant(magpie, 2);
			player.setItemSlot(EquipmentSlot.LEGS, ovve);
			ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
			legs.inventoryTick(helper.getLevel(), player, EquipmentSlot.LEGS);
			ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.getItem() != ModContent.top(Chapter.DATA)) { wrong.add("no companion top: " + chest); continue; }
			var context = player.connection.getPacketContext();
			var access = helper.getLevel().registryAccess();
			ItemStack clientLegs = PolymerItemUtils.getPolymerItemStack(legs, context, access);
			ItemStack clientTop = PolymerItemUtils.getPolymerItemStack(chest, context, access);
			ItemStack clientCuffs = PolymerItemUtils.getPolymerItemStack(OvveFeet.cuffsFor(legs), context, access);
			String mode = enchanted ? "enchanted: " : "plain: ";
			if (clientLegs.hasFoil() != enchanted) wrong.add(mode + "legs foil " + clientLegs.hasFoil());
			if (clientTop.hasFoil() != enchanted) wrong.add(mode + "top foil " + clientTop.hasFoil());
			if (clientCuffs.hasFoil() != enchanted) wrong.add(mode + "cuffs foil " + clientCuffs.hasFoil());
			if (enchanted) {
				if (!Boolean.TRUE.equals(clientTop.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) wrong.add("top has no glint override");
				if (!Boolean.TRUE.equals(clientCuffs.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) wrong.add("cuffs have no glint override");
				if (clientLegs.getEnchantments().getLevel(magpie) != 2) wrong.add("legs' client magpie level " + clientLegs.getEnchantments().getLevel(magpie));
			}
		}
		JsonObject en = lang("en_us"), sv = lang("sv_se");
		for (ResourceKey<Enchantment> key : ALL) {
			String langKey = "enchantment.ovvar." + key.identifier().getPath();
			Names names = NAMES.get(key);
			if (en == null || !en.has(langKey) || !en.get(langKey).getAsString().equals(names.en())) wrong.add("en_us " + langKey);
			if (sv == null || !sv.has(langKey) || !sv.get(langKey).getAsString().equals(names.sv())) wrong.add("sv_se " + langKey);
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	private static JsonObject lang(String language) {
		try (var in = EnchantmentTests.class.getResourceAsStream("/assets/ovvar/lang/" + language + ".json")) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			return null;
		}
	}

	@GameTest
	public void anvilRefusesKeyringOnEnderPockets(GameTestHelper helper) {
		Holder<Enchantment> enderPockets = holder(helper, ENDER_POCKETS), keyring = holder(helper, KEYRING), magpie = holder(helper, MAGPIE);
		ItemStack refused = anvil(helper, enderPockets, keyring);
		helper.assertTrue(refused.isEmpty(), "the anvil put Keyring on an Ender Pockets ovve: " + refused);
		ItemStack made = anvil(helper, enderPockets, magpie);
		helper.assertValueEqual(made.getItem(), ModContent.ovve(Chapter.DATA), "the anvil's result");
		helper.assertValueEqual(made.getEnchantments().getLevel(enderPockets), 1, "ender pockets on the result");
		helper.assertValueEqual(made.getEnchantments().getLevel(magpie), 1, "magpie on the result");
		helper.succeed();
	}

	private static ItemStack anvil(GameTestHelper helper, Holder<Enchantment> onOvve, Holder<Enchantment> onBook) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(onOvve, 1);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(ovve);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(EnchantmentHelper.createBook(new EnchantmentInstance(onBook, 1)));
		menu.createResult();
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}

	@GameTest
	public void patchworkScalesWithPatches(GameTestHelper helper) {
		EnchantmentsConfig.Patchwork cfg = EnchantmentsConfig.Patchwork.DEFAULT;
		float[][] cases = {{0, 1, 0f}, {4, 1, 1.0f}, {8, 1, 2.0f}, {16, 1, 4.0f}, {40, 1, 4.0f}, {16, 0, 0f}};
		for (float[] c : cases) {
			helper.assertValueEqual(PatchworkProtection.protection((int) c[0], (int) c[1], cfg), c[2], "protection(" + (int) c[0] + ", " + (int) c[1] + ")");
		}
		helper.succeed();
	}

	@GameTest
	public void patchworkProtectsTheWearer(GameTestHelper helper) {
		Holder<Enchantment> patchwork = holder(helper, PATCHWORK_PROTECTION);
		var itk = Patches.get("itk");
		List<Placement> placements = Stream.of(Spot.FRONT_TOP_LEFT, Spot.FRONT_TOP_RIGHT, Spot.FRONT_LOW_LEFT, Spot.FRONT_LOW_RIGHT,
				Spot.SLEEVE_OUT_TOP_R, Spot.SLEEVE_OUT_TOP_L, Spot.LEG_OUT_TOP_R, Spot.LEG_OUT_TOP_L).map(s -> new Placement(s, itk)).toList();
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.DATA));
		Looks.setSewn(plain, SpotPlacements.fromList(placements).getOrThrow());
		ItemStack enchanted = plain.copy();
		enchanted.enchant(patchwork, 1);
		var level = helper.getLevel();
		OvveItem.wear(stand, enchanted);
		float got = EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().generic());
		helper.assertTrue(Math.abs(got - 2.0f) < 1e-4f, "protection with 8 patches was " + got);
		helper.assertValueEqual(EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().fellOutOfWorld()), 0f, "protection against the void");
		OvveItem.wear(stand, plain);
		helper.assertValueEqual(EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().generic()), 0f, "protection without the enchantment");
		helper.succeed();
	}

	// ---- Magpie

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) < 1e-6f, what + " was " + got + ", wanted " + want);
	}

	@GameTest
	public void magpieChances(GameTestHelper helper) {
		EnchantmentsConfig.Magpie cfg = EnchantmentsConfig.Magpie.DEFAULT;
		near(helper, Magpie.chestChance(0, cfg), 0.05f, "chestChance(0)");
		near(helper, Magpie.chestChance(1, cfg), 0.08f, "chestChance(1)");
		near(helper, Magpie.chestChance(3, cfg), 0.14f, "chestChance(3)");
		near(helper, Magpie.mobOvvarChance(0.25f, 2, cfg), 0.45f, "mobOvvarChance(0.25, 2)");
		near(helper, Magpie.mobOvvarChance(0.95f, 3, cfg), 1.0f, "mobOvvarChance(0.95, 3)");
		// patch_drop_chance 0 means "never": Magpie lifts a chance, it does not make one.
		near(helper, Magpie.mobOvvarChance(0f, 3, cfg), 0f, "mobOvvarChance(0, 3)");
		helper.assertValueEqual(Magpie.level(null), 0, "level(null)");
		helper.succeed();
	}

	/** An armour stand in a Data ovve (or a Media frack) with {@code magpie} levels of Magpie; 0 means plain. */
	private static ArmorStand standIn(GameTestHelper helper, Chapter chapter, int magpie) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack garment = new ItemStack(ModContent.ovve(chapter));
		if (magpie > 0) garment.enchant(holder(helper, MAGPIE), magpie);
		OvveItem.wear(stand, garment);
		return stand;
	}

	/** The share of 4000 dungeon chests, opened by {@code opener}, that hold a patch; no NON_LOOT patch may ever show. */
	private static double patchShare(GameTestHelper helper, Entity opener) {
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
		LootParams.Builder builder = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)));
		if (opener != null) builder.withParameter(LootContextParams.THIS_ENTITY, opener);
		LootParams params = builder.create(LootContextParamSets.CHEST);
		int with = 0;
		for (int seed = 0; seed < 4000; seed++) {
			boolean any = false;
			for (ItemStack stack : table.getRandomItems(params, seed)) {
				if (stack.getItem() instanceof PatchItem item) {
					any = true;
					helper.assertFalse(PatchLoot.NON_LOOT.contains(item.patch.id()), "a dungeon chest held " + item.patch.id() + ", which is not loot");
				}
			}
			if (any) with++;
		}
		return with / 4000.0;
	}

	private static void share(GameTestHelper helper, double got, double lo, double hi, String what) {
		helper.assertTrue(got >= lo && got <= hi, what + ": " + got + " not in [" + lo + ", " + hi + "]");
	}

	@GameTest
	public void magpieRaisesChestPatches(GameTestHelper helper) {
		share(helper, patchShare(helper, standIn(helper, Chapter.DATA, 3)), 0.12, 0.16, "Magpie III opener");
		share(helper, patchShare(helper, standIn(helper, Chapter.DATA, 0)), 0.035, 0.065, "plain ovve opener");
		share(helper, patchShare(helper, null), 0.035, 0.065, "no opener");
		helper.succeed();
	}

	@GameTest
	public void magpieOnAFrackCounts(GameTestHelper helper) {
		share(helper, patchShare(helper, standIn(helper, Chapter.MEDIA, 3)), 0.12, 0.16, "Magpie III frack opener");
		helper.succeed();
	}

	/** A /give'n Magpie IV (above the max) counts as III; Magpie on anything but an ovve or frack counts for nothing. */
	@GameTest
	public void magpieAboveMaxAndOffGarment(GameTestHelper helper) {
		share(helper, patchShare(helper, standIn(helper, Chapter.DATA, 4)), 0.12, 0.16, "Magpie IV opener");
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
		leggings.enchant(holder(helper, MAGPIE), 3);
		stand.setItemSlot(EquipmentSlot.LEGS, leggings);
		share(helper, patchShare(helper, stand), 0.035, 0.065, "Magpie III iron leggings opener");
		helper.succeed();
	}

	/**
	 * Mob ovvar's patch drop (a real mob garment, {@link MobOvvar#patchDrops} at {@link MobOvvar#patchDropChance}) with a
	 * roll that always comes in at {@code base + 0.05}: a killer in a plain ovve, or no killer, gets nothing; a killer in a
	 * Magpie I ovve gets every patch. The chance is {@code base + 0.10 x level}, capped at 1, and unchanged without Magpie.
	 */
	@GameTest
	public void magpieRaisesMobOvvarDrops(GameTestHelper helper) {
		float base = 0.25f;
		RandomSource roll = new SingleThreadedRandomSource(0) {
			@Override
			public float nextFloat() {
				return base + 0.05f;
			}
		};
		se.metacraft.ovvar.MobOvvarConfig mob = new se.metacraft.ovvar.MobOvvarConfig(true, 0.05f, base);
		EnchantmentsConfig.Magpie cfg = EnchantmentsConfig.Magpie.DEFAULT;
		ItemStack garment = MobOvvar.garment(RandomSource.create(7), EquipmentSlot.LEGS, true);
		helper.assertTrue(MobOvvar.is(garment), "a real mob garment");
		int worn = SpotPlacements.asPlacementList(Looks.sewn(garment)).size();
		helper.assertTrue(worn > 0, "the mob garment wears patches");
		near(helper, MobOvvar.patchDropChance(mob, standIn(helper, Chapter.DATA, 0), cfg), base, "chance, plain ovve");
		near(helper, MobOvvar.patchDropChance(mob, null, cfg), base, "chance, no killer");
		near(helper, MobOvvar.patchDropChance(mob, standIn(helper, Chapter.DATA, 2), cfg), base + 0.20f, "chance, Magpie II");
		near(helper, MobOvvar.patchDropChance(mob, standIn(helper, Chapter.MEDIA, 3), cfg), base + 0.30f, "chance, Magpie III frack");
		near(helper, MobOvvar.patchDropChance(new se.metacraft.ovvar.MobOvvarConfig(true, 0.05f, 0.9f), standIn(helper, Chapter.DATA, 3), cfg), 1f, "chance capped at 1");
		helper.assertValueEqual(MobOvvar.patchDrops(garment, roll, MobOvvar.patchDropChance(mob, standIn(helper, Chapter.DATA, 0), cfg)).size(), 0, "drops for a plain-ovve killer");
		helper.assertValueEqual(MobOvvar.patchDrops(garment, roll, MobOvvar.patchDropChance(mob, null, cfg)).size(), 0, "drops without a killer");
		helper.assertValueEqual(MobOvvar.patchDrops(garment, roll, MobOvvar.patchDropChance(mob, standIn(helper, Chapter.DATA, 1), cfg)).size(), worn, "drops for a Magpie I killer");
		helper.succeed();
	}

	// ---- Keepsake

	private static ItemStack keepsakeOvve(GameTestHelper helper, int dirt) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(holder(helper, KEEPSAKE), 1);
		if (dirt > 0) {
			var bundle = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, net.minecraft.world.item.component.BundleContents.EMPTY).asMutable();
			bundle.tryInsert(new ItemStack(Items.DIRT, dirt));
			ovve.set(DataComponents.BUNDLE_CONTENTS, bundle.toImmutable());
		}
		return ovve;
	}

	private static int dirtIn(ItemStack ovve) {
		int n = 0;
		var contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		if (contents != null) for (var s : contents.items()) n += s.count();
		return n;
	}

	private static int legsIndex() {
		for (var e : net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
			if (e.getValue() == EquipmentSlot.LEGS) return e.getIntKey();
		}
		throw new IllegalStateException("no legs slot");
	}

	/** The item entities around {@code at} that were not there before (tests share the world, and it may hold litter from earlier runs). */
	private static List<net.minecraft.world.entity.item.ItemEntity> itemsNear(GameTestHelper helper, Entity at, double radius, Set<java.util.UUID> before) {
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, at.getBoundingBox().inflate(radius))
				.stream().filter(e -> !before.contains(e.getUUID())).toList();
	}

	private static Set<java.util.UUID> itemIds(GameTestHelper helper, Entity at, double radius) {
		Set<java.util.UUID> ids = new HashSet<>();
		for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, at.getBoundingBox().inflate(radius))) ids.add(e.getUUID());
		return ids;
	}

	@GameTest
	public void keepsakeOvveSurvivesDeath(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.LEGS, keepsakeOvve(helper, 3));
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.IT));
		player.getInventory().setItem(5, plain);
		player.getInventory().setItem(6, new ItemStack(Items.DIRT, 10));
		Set<java.util.UUID> before = itemIds(helper, player, 8);
		player.die(helper.getLevel().damageSources().generic());

		List<Keepsake.Kept> kept = player.getAttachedOrElse(Keepsake.KEPT, List.of());
		helper.assertValueEqual(kept.size(), 1, "kept entries");
		helper.assertValueEqual(kept.getFirst().slot(), legsIndex(), "kept slot");
		helper.assertValueEqual(dirtIn(kept.getFirst().stack()), 3, "dirt in the kept ovve");
		boolean itOvve = false, dirt = false, data = false;
		for (var item : itemsNear(helper, player, 8, before)) {
			ItemStack s = item.getItem();
			if (s.is(ModContent.ovve(Chapter.IT))) itOvve = true;
			if (s.is(Items.DIRT) && s.getCount() == 10) dirt = true;
			if (s.is(ModContent.ovve(Chapter.DATA))) data = true;
		}
		// With Universal Graves (-PwithGraves) the rest goes into a grave instead: keepsakeOutlivesAGrave.
		if (!GRAVES) {
			helper.assertTrue(itOvve, "the plain IT ovve dropped");
			helper.assertTrue(dirt, "the 10 dirt dropped");
		}
		helper.assertFalse(data, "the Keepsake ovve dropped");

		ServerPlayer fresh = helper.makeMockServerPlayerInLevel();
		Keepsake.restore(player, fresh);
		ItemStack legs = fresh.getItemBySlot(EquipmentSlot.LEGS);
		helper.assertTrue(legs.is(ModContent.ovve(Chapter.DATA)), "the ovve is back in the legs slot: " + legs);
		helper.assertValueEqual(dirtIn(legs), 3, "dirt in the restored ovve");
		helper.assertFalse(player.hasAttached(Keepsake.KEPT), "the dead player still has KEPT");
		helper.succeed();
	}

	@GameTest
	public void keepsakeSurvivesARealRespawn(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.LEGS, keepsakeOvve(helper, 3));
		player.die(helper.getLevel().damageSources().generic());
		ServerPlayer fresh = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		try {
			ItemStack legs = fresh.getItemBySlot(EquipmentSlot.LEGS);
			helper.assertTrue(legs.is(ModContent.ovve(Chapter.DATA)), "the respawned player wears the ovve: " + legs);
			helper.assertValueEqual(dirtIn(legs), 3, "dirt in the respawned ovve");
			helper.assertFalse(player.hasAttached(Keepsake.KEPT), "the old player still has KEPT");
		} finally {
			if (fresh != player) fresh.discard();
		}
		helper.succeed();
	}

	static final boolean GRAVES = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("universal-graves");

	/** What Universal Graves holds for {@code owner}, read reflectively (it is only on the runtime under -PwithGraves). */
	static List<ItemStack> graveItems(GameTestHelper helper, java.util.UUID owner) {
		List<ItemStack> items = new java.util.ArrayList<>();
		try {
			Object manager = Class.forName("eu.pb4.graves.grave.GraveManager").getField("INSTANCE").get(null);
			for (Object grave : (java.util.Collection<?>) manager.getClass().getMethod("getByUuid", java.util.UUID.class).invoke(manager, owner)) {
				for (Object positioned : (List<?>) grave.getClass().getMethod("getItems").invoke(grave)) {
					items.add((ItemStack) positioned.getClass().getMethod("stack").invoke(positioned));
				}
			}
		} catch (ReflectiveOperationException e) {
			helper.fail("can't read Universal Graves' graves: " + e);
		}
		return items;
	}

	/** Universal Graves takes the inventory before vanilla's death drop: the Keepsake ovve must be set aside before that. */
	@GameTest
	public void keepsakeOutlivesAGrave(GameTestHelper helper) {
		if (!GRAVES) {
			helper.succeed();
			return;
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.LEGS, keepsakeOvve(helper, 3));
		player.getInventory().setItem(6, new ItemStack(Items.DIRT, 10));
		player.die(helper.getLevel().damageSources().generic());
		ServerPlayer fresh = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		try {
			ItemStack legs = fresh.getItemBySlot(EquipmentSlot.LEGS);
			helper.assertTrue(legs.is(ModContent.ovve(Chapter.DATA)), "the respawned player wears the ovve: " + legs);
			helper.assertValueEqual(dirtIn(legs), 3, "dirt in the respawned ovve");
		} finally {
			if (fresh != player) fresh.discard();
		}
		// Graves may place the grave a tick later.
		helper.succeedWhen(() -> {
			List<ItemStack> grave = graveItems(helper, player.getUUID());
			helper.assertTrue(grave.stream().anyMatch(s -> s.is(Items.DIRT) && s.getCount() == 10), "the dirt is in a grave (Graves ran): " + grave);
			helper.assertFalse(grave.stream().anyMatch(s -> s.is(ModContent.ovve(Chapter.DATA))), "the Keepsake ovve is not in the grave: " + grave);
		});
	}

	/** keepInventory on: nothing is set aside, the ovve simply stays where it is. */
	@GameTest
	public void keepsakeLeavesKeepInventoryAlone(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.LEGS, keepsakeOvve(helper, 3));
		var rules = helper.getLevel().getGameRules();
		boolean was = rules.get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY);
		rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, true, helper.getLevel().getServer());
		try {
			player.die(helper.getLevel().damageSources().generic());
		} finally {
			rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, was, helper.getLevel().getServer());
		}
		helper.assertFalse(player.hasAttached(Keepsake.KEPT), "nothing stashed under keepInventory");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.ovve(Chapter.DATA)), "the ovve stays worn");
		helper.succeed();
	}

	@GameTest
	public void keepsakeStashAppends(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack first = keepsakeOvve(helper, 1);
		player.setAttached(Keepsake.KEPT, List.of(new Keepsake.Kept(7, first)));
		player.getInventory().setItem(3, keepsakeOvve(helper, 2));
		Keepsake.stash(player);
		helper.assertValueEqual(player.getAttachedOrElse(Keepsake.KEPT, List.of()).size(), 2, "KEPT entries after a second stash");
		helper.succeed();
	}

	@GameTest
	public void keepsakeRestoreNeverDeletes(GameTestHelper helper) {
		ServerPlayer from = helper.makeMockServerPlayerInLevel();
		ItemStack a = keepsakeOvve(helper, 1), b = keepsakeOvve(helper, 2);
		from.setAttached(Keepsake.KEPT, List.of(new Keepsake.Kept(legsIndex(), a), new Keepsake.Kept(5, b)));
		ServerPlayer fresh = helper.makeMockServerPlayerInLevel();
		fresh.setGameMode(GameType.SURVIVAL);
		ItemStack c = new ItemStack(ModContent.ovve(Chapter.IT));
		fresh.setItemSlot(EquipmentSlot.LEGS, c);
		for (int i = 0; i < 36; i++) if (i != 5) fresh.getInventory().setItem(i, new ItemStack(Items.STONE));
		Set<java.util.UUID> before = itemIds(helper, fresh, 2);
		Keepsake.restore(from, fresh);

		helper.assertValueEqual(dirtIn(fresh.getInventory().getItem(5)), 2, "slot 5 holds ovve B");
		helper.assertTrue(fresh.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.ovve(Chapter.IT)), "the worn ovve C stayed");
		int ovvar = 0, droppedA = 0;
		for (int i = 0; i < fresh.getInventory().getContainerSize(); i++) {
			if (fresh.getInventory().getItem(i).getItem() instanceof OvveItem) ovvar++;
		}
		for (var item : itemsNear(helper, fresh, 2, before)) {
			if (item.getItem().getItem() instanceof OvveItem) {
				ovvar++;
				if (dirtIn(item.getItem()) == 1) droppedA++;
			}
		}
		helper.assertValueEqual(droppedA, 1, "ovve A lying near the player");
		helper.assertValueEqual(ovvar, 3, "ovvar across inventory and ground");
		helper.assertFalse(from.hasAttached(Keepsake.KEPT), "KEPT removed from the old player");
		helper.succeed();
	}

	// ---- Curse of the Spilled Drink

	private static SpilledDrink.Dose dose(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp, int duration, float scale, long due) {
		return new SpilledDrink.Dose(effect, amp, duration, scale, due);
	}

	@GameTest
	public void secondDoseIsWeakerAndShorter(GameTestHelper helper) {
		EnchantmentsConfig.SpilledDrink cfg = EnchantmentsConfig.SpilledDrink.DEFAULT;
		helper.assertValueEqual(SpilledDrink.secondDoses(List.of(new MobEffectInstance(MobEffects.SPEED, 1800, 1)), 1000, cfg),
				List.of(dose(MobEffects.SPEED, 0, 900, 1f, 2900)), "strong swiftness");
		helper.assertValueEqual(SpilledDrink.secondDoses(List.of(new MobEffectInstance(MobEffects.SPEED, 3600, 0)), 1000, cfg),
				List.of(dose(MobEffects.SPEED, 0, 1800, 1f, 4700)), "swiftness");
		helper.assertValueEqual(SpilledDrink.secondDoses(List.of(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0)), 1000, cfg),
				List.of(dose(MobEffects.INSTANT_HEALTH, 0, 1, 0.5f, 1100)), "healing");
		helper.assertValueEqual(SpilledDrink.secondDoses(List.of(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0)), 1000, cfg).size(), 0, "infinite");
		helper.assertValueEqual(SpilledDrink.secondDoses(List.of(), 1000, cfg).size(), 0, "water");
		helper.succeed();
	}

	private static ServerPlayer cursedPlayer(GameTestHelper helper, boolean cursed) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		if (cursed) ovve.enchant(holder(helper, SPILLED_DRINK_CURSE), 1);
		player.setItemSlot(EquipmentSlot.LEGS, ovve);
		return player;
	}

	private static void drinkSwiftness(GameTestHelper helper, ServerPlayer player) {
		ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
		new PotionContents(Potions.SWIFTNESS).onConsume(helper.getLevel(), player, potion, potion.get(DataComponents.CONSUMABLE));
	}

	/**
	 * A Brewery drink has no potion_contents: its effects come from its drink type, through Brewery's own
	 * consumption effects. Only with Brewery on the runtime (-PovvarBrewery); green tea gives Resistance.
	 */
	@GameTest
	public void breweryDrinkSchedulesTheDose(GameTestHelper helper) {
		if (!OvveEnchantments.BREWERY) {
			helper.succeed();
			return;
		}
		ItemStack tea;
		try {
			tea = (ItemStack) Class.forName("eu.pb4.brewery.drink.DrinkUtils")
					.getMethod("createDrink", net.minecraft.resources.Identifier.class, int.class, double.class, int.class, net.minecraft.world.level.block.Block.class)
					.invoke(null, net.minecraft.resources.Identifier.fromNamespaceAndPath("brewery", "green_tea"), 0, 10.0, 0, net.minecraft.world.level.block.Blocks.FIRE);
		} catch (ReflectiveOperationException e) {
			helper.fail("can't make Brewery's green tea: " + e);
			return;
		}
		ServerPlayer cursed = cursedPlayer(helper, true);
		cursed.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tea);
		tea.finishUsingItem(helper.getLevel(), cursed);
		helper.assertTrue(cursed.hasEffect(MobEffects.RESISTANCE), "the tea gave Resistance (the drink is right): " + cursed.getActiveEffects());
		List<SpilledDrink.Dose> doses = cursed.getAttachedOrElse(SpilledDrink.DOSES, List.of());
		helper.assertValueEqual(doses.size(), 1, "doses after a Brewery drink");
		helper.assertTrue(doses.getFirst().effect().is(MobEffects.RESISTANCE.unwrapKey().orElseThrow()), "the dose is Resistance: " + doses);

		ServerPlayer plain = cursedPlayer(helper, false);
		ItemStack more = tea.copy();
		plain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, more);
		more.finishUsingItem(helper.getLevel(), plain);
		helper.assertValueEqual(plain.getAttachedOrElse(SpilledDrink.DOSES, List.of()).size(), 0, "doses without the curse");
		helper.succeed();
	}

	@GameTest
	public void drinkingSchedulesTheDose(GameTestHelper helper) {
		ServerPlayer cursed = cursedPlayer(helper, true);
		drinkSwiftness(helper, cursed);
		List<SpilledDrink.Dose> doses = cursed.getAttachedOrElse(SpilledDrink.DOSES, List.of());
		if (OvveEnchantments.BREWERY) {
			helper.assertValueEqual(doses.size(), 1, "doses with Brewery");
			helper.assertTrue(doses.getFirst().effect().is(MobEffects.SPEED.unwrapKey().orElseThrow()), "the dose is speed");
		} else {
			helper.assertValueEqual(doses.size(), 0, "doses without Brewery (the curse is off)");
		}
		ServerPlayer plain = cursedPlayer(helper, false);
		drinkSwiftness(helper, plain);
		helper.assertValueEqual(plain.getAttachedOrElse(SpilledDrink.DOSES, List.of()).size(), 0, "doses without the curse");
		helper.succeed();
	}

	private static void tickOnly(GameTestHelper helper, ServerPlayer player) {
		var players = helper.getLevel().getServer().getPlayerList().getPlayers();
		boolean added = !players.contains(player);
		if (added) players.add(player);
		try {
			SpilledDrink.tick(helper.getLevel().getServer());
		} finally {
			if (added) players.remove(player);
		}
	}

	@GameTest
	public void dueDoseLands(GameTestHelper helper) {
		ServerPlayer player = cursedPlayer(helper, false);
		long now = helper.getLevel().getServer().overworld().getGameTime();
		player.setAttached(SpilledDrink.DOSES, List.of(dose(MobEffects.SPEED, 0, 1800, 1f, now), dose(MobEffects.SLOWNESS, 0, 100, 1f, now + 1000)));
		tickOnly(helper, player);
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		helper.assertTrue(speed != null && speed.getAmplifier() == 0 && speed.getDuration() >= 1799, "speed landed: " + speed);
		helper.assertTrue(player.getEffect(MobEffects.SLOWNESS) == null, "the future dose landed early");
		List<SpilledDrink.Dose> left = player.getAttachedOrElse(SpilledDrink.DOSES, List.of());
		helper.assertValueEqual(left.size(), 1, "doses left");
		helper.assertTrue(left.getFirst().effect().is(MobEffects.SLOWNESS.unwrapKey().orElseThrow()), "the future one is left");
		helper.succeed();
	}

	@GameTest
	public void secondDoseNeverWeakensActive(GameTestHelper helper) {
		ServerPlayer player = cursedPlayer(helper, false);
		long now = helper.getLevel().getServer().overworld().getGameTime();
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 2000, 1));
		player.setAttached(SpilledDrink.DOSES, List.of(dose(MobEffects.SPEED, 0, 900, 1f, now)));
		tickOnly(helper, player);
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		helper.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() >= 1999, "active speed after a weaker dose: " + speed);
		// A weaker dose that outlasts it is kept hidden underneath; the active one is still untouched.
		player.setAttached(SpilledDrink.DOSES, List.of(dose(MobEffects.SPEED, 0, 5000, 1f, now)));
		tickOnly(helper, player);
		speed = player.getEffect(MobEffects.SPEED);
		helper.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() >= 1998, "active speed after a longer weaker dose: " + speed);
		helper.succeed();
	}

	@GameTest
	public void dripsNowAndThen(GameTestHelper helper) {
		RandomSource r = RandomSource.create(0);
		int hits = 0;
		for (int i = 0; i < 20000; i++) if (SpilledDrink.nuisance(r, 200)) hits++;
		helper.assertTrue(hits >= 70 && hits <= 130, "drips in 20000 ticks: " + hits);
		helper.succeed();
	}

	@GameTest
	public void curseBookOnlyWhenOffered(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		Optional<ItemStack> book = SpilledDrink.wandererBook(registries, EnchantmentsConfig.DEFAULT, true);
		helper.assertTrue(book.isPresent(), "a book with Brewery");
		helper.assertTrue(book.get().is(Items.ENCHANTED_BOOK), "an enchanted book");
		helper.assertValueEqual(book.get().get(DataComponents.STORED_ENCHANTMENTS).getLevel(holder(helper, SPILLED_DRINK_CURSE)), 1, "stored level");
		helper.assertTrue(SpilledDrink.wandererBook(registries, EnchantmentsConfig.DEFAULT, false).isEmpty(), "a book without Brewery");
		EnchantmentsConfig off = new EnchantmentsConfig(EnchantmentsConfig.Toggle.ON, EnchantmentsConfig.Patchwork.DEFAULT, EnchantmentsConfig.Magpie.DEFAULT,
				EnchantmentsConfig.Toggle.ON, EnchantmentsConfig.Keyring.DEFAULT, new EnchantmentsConfig.SpilledDrink(false, 100, 0.5f, 0.5f, 200));
		helper.assertTrue(SpilledDrink.wandererBook(registries, off, true).isEmpty(), "a book with the curse disabled");
		helper.assertValueEqual(SpilledDrink.WANDERER_PRICE, 16, "price");
		helper.succeed();
	}

	/**
	 * Vanilla's mob-spawn equipment enchanting never enchants a mob's garment (Binding included). First the provider
	 * itself on real mob ovves and fracks ({@link MobOvvar#garment}), an iron helmet the control; then the real spawn:
	 * zombies spawned at mob_ovvar chance 1 through finalizeSpawn (which runs vanilla's enchant pass over every slot)
	 * wear unenchanted garments, while some of their vanilla gear does get enchanted.
	 */
	@GameTest(maxTicks = 1200)
	public void mobSpawnEquipmentNeverEnchantsAnOvve(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		var difficulty = helper.getLevel().getCurrentDifficultyAt(helper.absolutePos(new BlockPos(1, 1, 1)));
		int helmetsEnchanted = 0;
		for (int seed = 0; seed < 1000; seed++) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.CHEST}) {
				ItemStack garment = MobOvvar.garment(RandomSource.create(seed), slot, true);
				helper.assertTrue(MobOvvar.is(garment), "seed " + seed + " gave no mob garment for " + slot);
				EnchantmentHelper.enchantItemFromProvider(garment, registries, VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, RandomSource.create(seed));
				helper.assertTrue(garment.getEnchantments().isEmpty(), "seed " + seed + " enchanted a mob " + slot + " garment: " + garment.getEnchantments());
			}
			ItemStack helmet = new ItemStack(Items.IRON_HELMET);
			EnchantmentHelper.enchantItemFromProvider(helmet, registries, VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, RandomSource.create(seed));
			if (!helmet.getEnchantments().isEmpty()) helmetsEnchanted++;
		}
		helper.assertTrue(helmetsEnchanted > 0, "the control (an iron helmet) was never enchanted, so the test proves nothing");
		MobOvvarTests.withMobOvvar(helper, new se.metacraft.ovvar.MobOvvarConfig(true, 1f, 0.5f), () -> {
			int garments = 0, vanillaEnchanted = 0;
			for (net.minecraft.world.entity.Mob mob : MobOvvarTests.spawned(helper, EntityTypes.ZOMBIE, true)) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack worn = mob.getItemBySlot(slot);
					if (MobOvvar.is(worn)) {
						garments++;
						if (!worn.getEnchantments().isEmpty()) helper.fail("a spawned zombie's " + slot + " garment is enchanted: " + worn.getEnchantments());
					} else if (!worn.isEmpty() && !worn.getEnchantments().isEmpty()) {
						vanillaEnchanted++;
					}
				}
			}
			if (garments == 0) helper.fail("no zombie spawned in a garment at chance 1");
			if (vanillaEnchanted == 0) helper.fail("no spawned zombie's vanilla gear was enchanted, so the spawn pass proves nothing");
		});
	}
}
