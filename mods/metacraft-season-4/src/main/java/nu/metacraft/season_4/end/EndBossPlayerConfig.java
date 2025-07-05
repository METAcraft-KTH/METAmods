package nu.metacraft.season_4.end;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.*;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.Pool;
import org.pcollections.*;
import nu.metacraft.core.item.components.ExpiresComponent;
import nu.metacraft.core.item.components.METAcraftComponents;
import nu.metacraft.core.music.MusicEntry;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.util.ExtraCodecs;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import nu.metacraft.season_4.Season4;
import nu.metacraft.bosses.item.BossItems;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record EndBossPlayerConfig(
		double healthPercent,
		PMap<Integer, Double> healthPercentOverrides,
		int bossLives,
		double maxDistanceFromBoss,
		double maxDistanceFromSpawn,
		double minSpawnDist,
		double maxSpawnDist,
		Map<EquipmentSlot, ItemEntry> equipment,
		PVector<ItemEntry> items,
		ComponentMap componentsToApply,
		Map<RegistryEntry<EntityAttribute>, List<EntityAttributeModifier>> attributeModifiers,
		Optional<MusicEntry> musicForBoss,
		PSet<String> tagsToRemove,
		Optional<String> bossDefeatedCommand,
		Optional<String> bossInitCommand
) {

	private static boolean isValidSlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND;
	}

	private static final Keyable VALID_SLOTS = Keyable.forStrings(
			() -> Arrays.stream(EquipmentSlot.values()).filter(
					EndBossPlayerConfig::isValidSlot
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
											EndBossPlayerConfig::isValidSlot
									).map(EquipmentSlot::getName).collect(
											Collectors.joining(", ")
									)
					);
				}
			}
	);

	private static final Codec<PVector<ItemEntry>> ITEM_CODEC = ExtraCodecs.createPCollectionCodec(ItemEntry.CODEC, TreePVector.empty());

	private static final Codec<PSet<String>> TAGS_CODEC = ExtraCodecs.createPCollectionCodec(Codec.STRING, HashTreePSet.empty());

	public static final Codec<EndBossPlayerConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.DOUBLE.fieldOf("health_percent").forGetter(EndBossPlayerConfig::healthPercent),
					ExtraCodecs.createListSerializedPMap(
							Codec.INT.fieldOf("life"),
							Codec.DOUBLE.fieldOf("percent"),
							HashTreePMap.empty()
					).optionalFieldOf("health_percent_per_life", HashTreePMap.empty()).forGetter(EndBossPlayerConfig::healthPercentOverrides),
					Codec.INT.fieldOf("boss_lives").forGetter(EndBossPlayerConfig::bossLives),
					Codec.DOUBLE.fieldOf("max_distance_from_boss").forGetter(EndBossPlayerConfig::maxDistanceFromBoss),
					Codec.DOUBLE.fieldOf("max_distance_from_spawn").forGetter(EndBossPlayerConfig::maxDistanceFromSpawn),
					Codec.DOUBLE.fieldOf("min_spawn_distance").forGetter(EndBossPlayerConfig::minSpawnDist),
					Codec.DOUBLE.fieldOf("max_spawn_distance").forGetter(EndBossPlayerConfig::maxSpawnDist),
					Codec.simpleMap(PLAYER_SLOT_CODEC, ItemEntry.CODEC, VALID_SLOTS).codec().optionalFieldOf("equipment", Map.of()).forGetter(EndBossPlayerConfig::equipment),
					ITEM_CODEC.fieldOf("items").forGetter(EndBossPlayerConfig::items),
					ComponentMap.CODEC.fieldOf("components_to_apply").forGetter(EndBossPlayerConfig::componentsToApply),
					Codec.unboundedMap(EntityAttribute.CODEC, EntityAttributeModifier.CODEC.listOf()).fieldOf("attribute_modifiers").forGetter(EndBossPlayerConfig::attributeModifiers),
					MusicEntry.CODEC.optionalFieldOf("music_for_boss").forGetter(EndBossPlayerConfig::musicForBoss),
					TAGS_CODEC.fieldOf("tags_to_remove").forGetter(EndBossPlayerConfig::tagsToRemove),
					Codec.STRING.optionalFieldOf("boss_defeated_command").forGetter(EndBossPlayerConfig::bossDefeatedCommand),
					Codec.STRING.optionalFieldOf("boss_init_command").forGetter(EndBossPlayerConfig::bossInitCommand)
			).apply(instance, EndBossPlayerConfig::new)
	);

	static final ConfigContainer.Builder<EndBossPlayerConfig> CONFIG_BUILDER = ConfigContainer.Builder.create(
		CODEC, null
	).registryAvailableConfigInitializer(
			lookup -> new EndBossPlayerConfig(
					0.025, HashTreePMap.singleton(1, 0.0), 3, 256,
					512, 5, 15,
					Map.of(
							EquipmentSlot.HEAD, new ItemEntry(
									new ItemStack(
											Items.NETHERITE_HELMET.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.BOSS_ARMOUR_ENCHANTS
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Helmet")
											).add(
													DataComponentTypes.EQUIPPABLE,
													EndBossPlayerState.addOverlay(
															Items.NETHERITE_HELMET.getComponents().get(DataComponentTypes.EQUIPPABLE),
															Season4.getID("boss_overlay")
													)
											).build()
									),
									List.of(DataComponentTypes.TRIM, DataComponentTypes.ITEM_MODEL, DataComponentTypes.EQUIPPABLE, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.HEAD_ARMOR
									).build())
							),
							EquipmentSlot.CHEST, new ItemEntry(
									new ItemStack(
											Items.NETHERITE_CHESTPLATE.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.BOSS_ARMOUR_ENCHANTS.plus(
																	Enchantments.THORNS, 3
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Chestplate")
											).add(
													DataComponentTypes.GLIDER, Unit.INSTANCE
											).build()
									),
									List.of(DataComponentTypes.TRIM, DataComponentTypes.ITEM_MODEL, DataComponentTypes.EQUIPPABLE, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.CHEST_ARMOR
									).build())
							),
							EquipmentSlot.LEGS, new ItemEntry(
									new ItemStack(
											Items.NETHERITE_LEGGINGS.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.BOSS_ARMOUR_ENCHANTS.plus(
																	Enchantments.SWIFT_SNEAK, 3
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Leggings")
											).build()
									),
									List.of(DataComponentTypes.TRIM, DataComponentTypes.ITEM_MODEL, DataComponentTypes.EQUIPPABLE, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.LEG_ARMOR
									).build())
							),
							EquipmentSlot.FEET, new ItemEntry(
									new ItemStack(
											Items.NETHERITE_BOOTS.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.BOSS_ARMOUR_ENCHANTS
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Boots")
											).build()
									),
									List.of(DataComponentTypes.TRIM, DataComponentTypes.ITEM_MODEL, DataComponentTypes.EQUIPPABLE, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.FOOT_ARMOR
									).build())
							),
							EquipmentSlot.OFFHAND, new ItemEntry(
									new ItemStack(
											Items.SHIELD.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Shield")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME, DataComponentTypes.BANNER_PATTERNS, DataComponentTypes.BASE_COLOR),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.SHIELD
									).build())
							)
					),
					TreePVector.singleton(
							new ItemEntry(
									new ItemStack(
											Items.NETHERITE_SWORD.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
																	Enchantments.SHARPNESS, 5
															).plus(
																	Enchantments.FIRE_ASPECT, 2
															).plus(
																	Enchantments.SWEEPING_EDGE, 2
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Sword")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.SWORDS
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.NETHERITE_AXE.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
																	Enchantments.SHARPNESS, 5
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Axe")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().tag(
											lookup.getOrThrow(RegistryKeys.ITEM),
											ItemTags.AXES
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.BOW.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
																	Enchantments.POWER, 5
															).plus(
																	Enchantments.PUNCH, 2
															).plus(
																	Enchantments.FLAME, 1
															).plus(
																	Enchantments.INFINITY, 1
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Bow")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.BOW
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.CROSSBOW.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
																	Enchantments.MULTISHOT, 1
															).plus(
																	Enchantments.QUICK_CHARGE, 3
															).plus(
																	Enchantments.PIERCING, 4
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Crossbow")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.CROSSBOW
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.MACE.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.UNBREAKABLE, Unit.INSTANCE
											).add(
													DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
															lookup, EndBossPlayerState.CUSTOM_BOSS_ITEM_ENCHANTS.plus(
																	Enchantments.WIND_BURST, 3
															).plus(
																	Enchantments.BREACH, 4
															)
													)
											).add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Mace")
											).build()
									),
									List.of(DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.MACE
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.ENCHANTED_GOLDEN_APPLE.getRegistryEntry(), 16,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Super Golden Apple")
											).build()
									),
									List.of(DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.ENCHANTED_GOLDEN_APPLE
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.GOLDEN_APPLE.getRegistryEntry(), 64,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Mutated Golden Apple")
											).build()
									),
									List.of(DataComponentTypes.CUSTOM_NAME),
									Optional.of(ItemPredicate.Builder.create().items(
											lookup.getOrThrow(RegistryKeys.ITEM),
											Items.GOLDEN_APPLE
									).build())
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											BossItems.EVOKER_FANGS_WAND.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Evoker Fangs")
											).add(
													DataComponentTypes.ITEM_MODEL, Items.BLAZE_ROD.getComponents().get(DataComponentTypes.ITEM_MODEL)
											).build()
									),
									List.of(),
									Optional.empty()
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											BossItems.SPAWN_REINFORCEMENTS_WAND.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Summon Reinforcements")
											).add(
													DataComponentTypes.ITEM_MODEL, Items.BLAZE_ROD.getComponents().get(DataComponentTypes.ITEM_MODEL)
											).build()
									),
									List.of(),
									Optional.empty()
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											BossItems.DOUBLE_TEAM_WAND.getRegistryEntry(), 1,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Double Team")
											).add(
													DataComponentTypes.ITEM_MODEL, Items.BLAZE_ROD.getComponents().get(DataComponentTypes.ITEM_MODEL)
											).build()
									),
									List.of(),
									Optional.empty()
							)
					).plus(
							new ItemEntry(
									new ItemStack(
											Items.TIPPED_ARROW.getRegistryEntry(), 99,
											ComponentChanges.builder().add(
													DataComponentTypes.ITEM_NAME, Text.literal("Arrows of Death")
											).add(
													DataComponentTypes.MAX_STACK_SIZE, 99
											).add(
													DataComponentTypes.POTION_CONTENTS,
													new PotionContentsComponent(Potions.STRONG_HARMING)
											).build()
									),
									List.of(),
									Optional.empty()
							)
					),
					ComponentMap.builder().add(
							METAcraftComponents.ANTI_KEEP_INVENTORY, Unit.INSTANCE
					).add(
							DataComponentTypes.RARITY, Rarity.EPIC
					).add(
							DataComponentTypes.LORE, new LoreComponent(List.of(
									Text.literal("This item has been mutated").styled(style -> style.withFormatting(Formatting.DARK_PURPLE).withItalic(false)),
									Text.literal("Your inventory will be restored later").styled(style -> style.withFormatting(Formatting.DARK_PURPLE).withItalic(false))
							))
					).add(
							METAcraftComponents.EXPIRES_AT,
							new ExpiresComponent(Instant.parse("2035-03-24T23:00:00.00Z"), Pool.<ItemStack>empty())
					).add(
							DataComponentTypes.ENCHANTMENTS, EndBossPlayerState.prepareEnchantments(
									lookup, EndBossPlayerState.ENCHANTS_APPLIED_TO_ALL_ITEMS
							)
					).build(),
					Map.of(
							EntityAttributes.MAX_HEALTH, List.of(
									new EntityAttributeModifier(
											Season4.getID("boss_health"), 480,
											EntityAttributeModifier.Operation.ADD_VALUE
									)
							)
					),
					Optional.empty(),
					HashTreePSet.singleton("challengemod.m").plus("challengemod.e"),
					Optional.empty(), Optional.empty()
			)
	).reloadAfterServer();

	public double getHealthPercent(int livesRemaining) {
		return healthPercentOverrides.getOrDefault(livesRemaining, healthPercent);
	}

	public record ItemEntry(
			ItemStack item, List<ComponentType<?>> componentsToCopy, Optional<ItemPredicate> toCopyFrom
	) {
		public static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ItemStack.CODEC.fieldOf("item").forGetter(ItemEntry::item),
						ComponentType.CODEC.listOf().optionalFieldOf("components_to_copy", List.of()).forGetter(ItemEntry::componentsToCopy),
						ItemPredicate.CODEC.optionalFieldOf("to_copy_from").forGetter(ItemEntry::toCopyFrom)
				).apply(instance, ItemEntry::new)
		);

		public ItemEntry(ItemStack item) {
			this(item, List.of(), Optional.empty());
		}

		public ItemStack getItem(PlayerInventory inv, int firstSlot) {
			return toCopyFrom.map(toCopyFrom -> {
				var stack = inv.getStack(firstSlot);
				if (!stack.isEmpty() && toCopyFrom.test(stack)) {
					return apply(stack, inv.player.getRegistryManager());
				}
				for (int i = 0; i < inv.size(); i++) {
					stack = inv.getStack(i);
					if (!stack.isEmpty() && toCopyFrom.test(stack)) {
						return apply(stack, inv.player.getRegistryManager());
					}
				}
				return null;
			}).orElse(item.copy());
		}

		public static <T> void mergeComponentIntoStack(ItemStack stack, Component<T> component, RegistryWrapper.WrapperLookup registries) {
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

		public static <T> void mergeComponentIntoStack(ItemStack stack, ComponentType<T> type, T value, RegistryWrapper.WrapperLookup registries) {
			if (stack.getComponentChanges().get(type) != null && !type.shouldSkipSerialization())  {
				var ops = registries.getOps(JavaOps.INSTANCE);
				var toOverwrite = type.getCodec().encodeStart(ops, stack.get(type)).resultOrPartial(Season4.LOGGER::error);
				if (toOverwrite.isEmpty()) {
					stack.set(type, value);
					return;
				}
				var toCopy = type.getCodec().encodeStart(ops, value).resultOrPartial(Season4.LOGGER::error);
				if (toCopy.isPresent()) {
					var result = merge(new Dynamic<>(ops, toOverwrite.get()), new Dynamic<>(ops, toCopy.get()));
					type.getCodec().parse(result).resultOrPartial(Season4.LOGGER::error).ifPresent(
							data -> stack.set(type, data)
					);
				}
			} else {
				stack.set(type, value);
			}
		}

		private static <T> void copyComponent(ItemStack source, ItemStack dest, ComponentType<T> type, RegistryWrapper.WrapperLookup registries) {
			if (source.contains(type)) {
				mergeComponentIntoStack(dest, type, source.get(type), registries);
			}
		}

		private ItemStack apply(ItemStack source, RegistryWrapper.WrapperLookup registries) {
			var newStack = item.copy();
			for (var type : componentsToCopy) {
				copyComponent(source, newStack, type, registries);
			}
			return newStack;
		}
	}

}
