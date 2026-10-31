package se.metacraft.player_bosses.state;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import org.pcollections.*;
import se.metacraft.bosses.item.BossItems;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.core.item.components.ExpiresComponent;
import se.metacraft.core.item.components.METAcraftComponents;
import se.metacraft.core.music.PlayerMusic;
import se.metacraft.lib.util.METACodecs;
import se.metacraft.lib.util.helper.PCollectionsHelper;
import se.metacraft.player_bosses.PlayerBosses;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public record PlayerBossConfig(
	double healthPercent,
	PMap<Integer, Double> healthPercentOverrides,
	int bossLives,
	double maxDistanceFromBoss,
	double maxDistanceFromSpawn,
	double minSpawnDist,
	double maxSpawnDist,
	Map<EquipmentSlot, ItemEntry> equipment,
	PVector<ItemEntry> items,
	DataComponentMap componentsToApply,
	Map<Holder<Attribute>, List<AttributeModifier>> attributeModifiers,
	Optional<PlayerMusic> musicForBoss,
	PSet<String> tagsToRemove,
	Optional<String> bossDefeatedCommand,
	Optional<String> bossInitCommand
) {

	private static boolean isValidSlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND;
	}

	private static final Keyable VALID_SLOTS = Keyable.forStrings(
		() -> Arrays.stream(EquipmentSlot.values()).filter(
			PlayerBossConfig::isValidSlot
		).map(EquipmentSlot::getName)
	);

	private static final Codec<EquipmentSlot> PLAYER_SLOT_CODEC = EquipmentSlot.CODEC.validate(
		slot -> {
			if (isValidSlot(slot)) {
				return DataResult.success(slot);
			} else {
				return DataResult.error(
					() -> "Invalid slot, please specify one of " +
						Arrays.stream(EquipmentSlot.values()).filter(
							PlayerBossConfig::isValidSlot
						).map(EquipmentSlot::getName).collect(
							Collectors.joining(", ")
						)
				);
			}
		}
	);

	private static final Codec<PVector<ItemEntry>> ITEM_CODEC = METACodecs.createPCollectionCodec(ItemEntry.CODEC, TreePVector.empty());

	private static final Codec<PSet<String>> TAGS_CODEC = METACodecs.createPCollectionCodec(Codec.STRING, HashTreePSet.empty());

	public static final MapCodec<PlayerBossConfig> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.DOUBLE.fieldOf("health_percent").forGetter(PlayerBossConfig::healthPercent),
			METACodecs.createListSerializedPMap(
				Codec.INT.fieldOf("life"),
				Codec.DOUBLE.fieldOf("percent"),
				HashTreePMap.empty()
			).optionalFieldOf("health_percent_per_life", HashTreePMap.empty()).forGetter(PlayerBossConfig::healthPercentOverrides),
			Codec.INT.fieldOf("boss_lives").forGetter(PlayerBossConfig::bossLives),
			Codec.DOUBLE.fieldOf("max_distance_from_boss").forGetter(PlayerBossConfig::maxDistanceFromBoss),
			Codec.DOUBLE.fieldOf("max_distance_from_spawn").forGetter(PlayerBossConfig::maxDistanceFromSpawn),
			Codec.DOUBLE.fieldOf("min_spawn_distance").forGetter(PlayerBossConfig::minSpawnDist),
			Codec.DOUBLE.fieldOf("max_spawn_distance").forGetter(PlayerBossConfig::maxSpawnDist),
			Codec.simpleMap(PLAYER_SLOT_CODEC, ItemEntry.CODEC, VALID_SLOTS).codec().optionalFieldOf("equipment", Map.of()).forGetter(PlayerBossConfig::equipment),
			ITEM_CODEC.fieldOf("items").forGetter(PlayerBossConfig::items),
			DataComponentMap.CODEC.fieldOf("components_to_apply").forGetter(PlayerBossConfig::componentsToApply),
			Codec.unboundedMap(Attribute.CODEC, AttributeModifier.CODEC.listOf()).fieldOf("attribute_modifiers").forGetter(PlayerBossConfig::attributeModifiers),
			PlayerMusic.EASY_CODEC.optionalFieldOf("music_for_boss").forGetter(PlayerBossConfig::musicForBoss),
			TAGS_CODEC.fieldOf("tags_to_remove").forGetter(PlayerBossConfig::tagsToRemove),
			Codec.STRING.optionalFieldOf("boss_defeated_command").forGetter(PlayerBossConfig::bossDefeatedCommand),
			Codec.STRING.optionalFieldOf("boss_init_command").forGetter(PlayerBossConfig::bossInitCommand)
		).apply(instance, PlayerBossConfig::new)
	);

	static final ConfigContainer.Builder<PlayerBossConfig> CONFIG_BUILDER = ConfigContainer.Builder.create(
		CODEC, null
	).registryAvailableConfigInitializer(
		lookup -> new PlayerBossConfig(
			0.025, HashTreePMap.singleton(1, 0.0), 1, 256,
			512, 5, 15,
			Map.of(
				EquipmentSlot.HEAD, new ItemEntry(
					new ItemStack(
						Items.NETHERITE_HELMET.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.BOSS_ARMOUR_ENCHANTS
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Helmet")
						).build()
					),
					List.of(DataComponents.TRIM, DataComponents.ITEM_MODEL, DataComponents.EQUIPPABLE, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.HEAD_ARMOR
					).build())
				),
				EquipmentSlot.CHEST, new ItemEntry(
					new ItemStack(
						Items.NETHERITE_CHESTPLATE.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.BOSS_ARMOUR_ENCHANTS.plus(
									Enchantments.THORNS, 3
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Chestplate")
						).set(
							DataComponents.GLIDER, Unit.INSTANCE
						).build()
					),
					List.of(DataComponents.TRIM, DataComponents.ITEM_MODEL, DataComponents.EQUIPPABLE, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.CHEST_ARMOR
					).build())
				),
				EquipmentSlot.LEGS, new ItemEntry(
					new ItemStack(
						Items.NETHERITE_LEGGINGS.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.BOSS_ARMOUR_ENCHANTS.plus(
									Enchantments.SWIFT_SNEAK, 3
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Leggings")
						).build()
					),
					List.of(DataComponents.TRIM, DataComponents.ITEM_MODEL, DataComponents.EQUIPPABLE, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.LEG_ARMOR
					).build())
				),
				EquipmentSlot.FEET, new ItemEntry(
					new ItemStack(
						Items.NETHERITE_BOOTS.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.BOSS_ARMOUR_ENCHANTS
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Boots")
						).build()
					),
					List.of(DataComponents.TRIM, DataComponents.ITEM_MODEL, DataComponents.EQUIPPABLE, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.FOOT_ARMOR
					).build())
				),
				EquipmentSlot.OFFHAND, new ItemEntry(
					new ItemStack(
						Items.SHIELD.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Shield")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME, DataComponents.BANNER_PATTERNS, DataComponents.BASE_COLOR),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.SHIELD
					).build())
				)
			),
			TreePVector.singleton(
				new ItemEntry(
					new ItemStack(
						Items.NETHERITE_SWORD.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
									Enchantments.SHARPNESS, 5
								).plus(
									Enchantments.FIRE_ASPECT, 2
								).plus(
									Enchantments.SWEEPING_EDGE, 2
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Sword")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.SWORDS
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.NETHERITE_AXE.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
									Enchantments.SHARPNESS, 5
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Axe")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						ItemTags.AXES
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.BOW.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
									Enchantments.POWER, 5
								).plus(
									Enchantments.PUNCH, 2
								).plus(
									Enchantments.FLAME, 1
								).plus(
									Enchantments.INFINITY, 1
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Bow")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.BOW
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.CROSSBOW.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
									Enchantments.MULTISHOT, 1
								).plus(
									Enchantments.QUICK_CHARGE, 3
								).plus(
									Enchantments.PIERCING, 4
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Crossbow")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.CROSSBOW
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.MACE.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.UNBREAKABLE, Unit.INSTANCE
						).set(
							DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
								lookup, PlayerBossState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
									Enchantments.WIND_BURST, 3
								).plus(
									Enchantments.BREACH, 4
								)
							)
						).set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Mace")
						).build()
					),
					List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.MACE
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.ENCHANTED_GOLDEN_APPLE.builtInRegistryHolder(), 16,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Super Golden Apple")
						).build()
					),
					List.of(DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.ENCHANTED_GOLDEN_APPLE
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.GOLDEN_APPLE.builtInRegistryHolder(), 64,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Mutated Golden Apple")
						).build()
					),
					List.of(DataComponents.CUSTOM_NAME),
					Optional.of(ItemPredicate.Builder.item().of(
						lookup.lookupOrThrow(Registries.ITEM),
						Items.GOLDEN_APPLE
					).build())
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						BossItems.EVOKER_FANGS_WAND.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Evoker Fangs")
						).set(
							DataComponents.ITEM_MODEL, Items.BLAZE_ROD.components().get(DataComponents.ITEM_MODEL)
						).build()
					),
					List.of(),
					Optional.empty()
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						BossItems.SPAWN_REINFORCEMENTS_WAND.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Summon Reinforcements")
						).set(
							DataComponents.ITEM_MODEL, Items.BLAZE_ROD.components().get(DataComponents.ITEM_MODEL)
						).build()
					),
					List.of(),
					Optional.empty()
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						BossItems.DOUBLE_TEAM_WAND.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Double Team")
						).set(
							DataComponents.ITEM_MODEL, Items.BLAZE_ROD.components().get(DataComponents.ITEM_MODEL)
						).build()
					),
					List.of(),
					Optional.empty()
				)
			).plus(
				new ItemEntry(
					new ItemStack(
						Items.TIPPED_ARROW.builtInRegistryHolder(), 99,
						DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Arrows of Death")
						).set(
							DataComponents.MAX_STACK_SIZE, 99
						).set(
							DataComponents.POTION_CONTENTS,
							new PotionContents(Potions.STRONG_HARMING)
						).build()
					),
					List.of(),
					Optional.empty()
				)
			),
			DataComponentMap.builder().set(
				METAcraftComponents.ANTI_KEEP_INVENTORY, Unit.INSTANCE
			).set(
				DataComponents.RARITY, Rarity.EPIC
			).set(
				DataComponents.LORE, new ItemLore(List.of(
					Component.literal("This item has been mutated").withStyle(style -> style.applyFormat(ChatFormatting.DARK_PURPLE).withItalic(false)),
					Component.literal("Your inventory will be restored later").withStyle(style -> style.applyFormat(ChatFormatting.DARK_PURPLE).withItalic(false))
				))
			).set(
				METAcraftComponents.EXPIRES_AT,
				new ExpiresComponent(Instant.parse("2035-03-24T23:00:00.00Z"), WeightedList.of())
			).set(
				DataComponents.ENCHANTMENTS, PlayerBossState.prepareEnchantments(
					lookup, PlayerBossState.ENCHANTS_APPLIED_TO_ALL_ITEMS
				)
			).build(),
			Map.of(
				Attributes.MAX_HEALTH, List.of(
					new AttributeModifier(
						PlayerBosses.getID("boss_health"), 480,
						AttributeModifier.Operation.ADD_VALUE
					)
				)
			),
			Optional.empty(),
			HashTreePSet.empty(),
			Optional.empty(), Optional.empty()
		)
	).reloadAfterServer();

	public double getHealthPercent(int livesRemaining) {
		return healthPercentOverrides.getOrDefault(livesRemaining, healthPercent);
	}

	public record ItemEntry(
		ItemStack item, List<DataComponentType<?>> componentsToCopy, Optional<ItemPredicate> toCopyFrom
	) {
		public static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				ItemStack.CODEC.fieldOf("item").forGetter(ItemEntry::item),
				DataComponentType.CODEC.listOf().optionalFieldOf("components_to_copy", List.of()).forGetter(ItemEntry::componentsToCopy),
				ItemPredicate.CODEC.optionalFieldOf("to_copy_from").forGetter(ItemEntry::toCopyFrom)
			).apply(instance, ItemEntry::new)
		);

		public ItemEntry(ItemStack item) {
			this(item, List.of(), Optional.empty());
		}

		public ItemStack getItem(Inventory inv, int firstSlot) {
			return toCopyFrom.map(toCopyFrom -> {
				var stack = inv.getItem(firstSlot);
				if (!stack.isEmpty() && toCopyFrom.test(stack)) {
					return apply(stack, inv.player.registryAccess());
				}
				for (int i = 0; i < inv.getContainerSize(); i++) {
					stack = inv.getItem(i);
					if (!stack.isEmpty() && toCopyFrom.test(stack)) {
						return apply(stack, inv.player.registryAccess());
					}
				}
				return null;
			}).orElse(item.copy());
		}

		public static <T> void mergeComponentIntoStack(ItemStack stack, TypedDataComponent<T> component, HolderLookup.Provider registries) {
			mergeComponentIntoStack(stack, component.type(), component.value(), registries);
		}

		private static <T> Dynamic<T> merge(Dynamic<T> toOverwrite, Dynamic<T> toCopy) {

			var overwrittenMap = toOverwrite.asMapOpt();
			var copiedMap = toCopy.asMapOpt();
			if (overwrittenMap.isSuccess() && copiedMap.isSuccess()) {
				var o = PCollectionsHelper.collectToMap(
					overwrittenMap.getOrThrow(), Pair::getFirst, Pair::getSecond
				);
				var c = PCollectionsHelper.collectToMap(
					copiedMap.getOrThrow(), Pair::getFirst, Pair::getSecond
				);
				Set<Dynamic<T>> keysToMerge = Sets.intersection(o.keySet(), c.keySet());
				for (var k : keysToMerge) {
					o = o.plus(k, merge(o.get(k), c.get(k)));
				}
				Set<Dynamic<T>> keysToCopy = Sets.difference(c.keySet(), o.keySet());
				for (var k : keysToCopy) {
					o = o.plus(k, c.get(k));
				}
				return toOverwrite.createMap(o);
			}

			var overwrittenList = toOverwrite.asStreamOpt();
			var copiedList = toCopy.asStreamOpt();

			if (overwrittenList.isSuccess() && copiedList.isSuccess()) {
				return toOverwrite.createList(Stream.concat(overwrittenList.getOrThrow(), copiedList.getOrThrow()));
			}

			return toCopy;
		}

		public static <T> void mergeComponentIntoStack(ItemStack stack, DataComponentType<T> type, T value, HolderLookup.Provider registries) {
			if (stack.getComponentsPatch().get(stack.getPrototype(), type) != null && !type.isTransient())  {
				var ops = registries.createSerializationContext(JavaOps.INSTANCE);
				var toOverwrite = type.codec().encodeStart(ops, stack.get(type)).resultOrPartial(PlayerBosses.LOGGER::error);
				if (toOverwrite.isEmpty()) {
					stack.set(type, value);
					return;
				}
				var toCopy = type.codec().encodeStart(ops, value).resultOrPartial(PlayerBosses.LOGGER::error);
				if (toCopy.isPresent()) {
					var result = merge(new Dynamic<>(ops, toOverwrite.get()), new Dynamic<>(ops, toCopy.get()));
					type.codec().parse(result).resultOrPartial(PlayerBosses.LOGGER::error).ifPresent(
						data -> stack.set(type, data)
					);
				}
			} else {
				stack.set(type, value);
			}
		}

		private static <T> void copyComponent(ItemStack source, ItemStack dest, DataComponentType<T> type, HolderLookup.Provider registries) {
			if (source.has(type)) {
				mergeComponentIntoStack(dest, type, source.get(type), registries);
			}
		}

		private ItemStack apply(ItemStack source, HolderLookup.Provider registries) {
			var newStack = item.copy();
			for (var type : componentsToCopy) {
				copyComponent(source, newStack, type, registries);
			}
			return newStack;
		}
	}

}
