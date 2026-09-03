package nu.metacraft.saved_items.item_saving;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.SavedDataTypeCache;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.saved_items.SavedItemsConfig;
import nu.metacraft.saved_items.SavedItems;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SavedItemsData extends SavedData {

	public static final String ITEMS = "Items"; //Careful, this is used by a datafixer!

	private static final SavedDataTypeCache.Type<SavedItemsData, MinecraftServer> TYPE = new SavedDataTypeCache.Type<>(
			server -> new SavedDataType<>(
					SavedItems.getID("saved_items"), () -> create(server),
					createCodec(server),
					DataFixTypes.METACRAFT_SAVED_DATA_SAVED_ITEMS
			)
	);

	private static final Codec<Multimap<Item, SavedItemEntry>> CODEC = METACodecs.unboundedMultimap(
			BuiltInRegistries.ITEM.byNameCodec(), SavedItemEntry.CODEC,
			MultimapBuilder.hashKeys().arrayListValues()::build
	);

	private static Codec<SavedItemsData> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						CODEC.fieldOf(ITEMS).forGetter(d -> d.items)
				).apply(instance, create(server)::load)
		);
	}

	public static SavedItemsData getInstance(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(SavedDataTypeCache.get(server, TYPE));
	}

	private final Multimap<Item, SavedItemEntry> items = MultimapBuilder.hashKeys().arrayListValues().build();

	private final MinecraftServer server;

	private SavedItemsData(MinecraftServer server) {
		this.server = server;
	}

	public static final SavedItemsConfig.SavingType DESPAWN_TYPE = SavedItemsConfig.SavingType.of(Identifier.withDefaultNamespace("despawn"));
	public static final SavedItemsConfig.SavingType ANY_DAMAGE = SavedItemsConfig.SavingType.of(Identifier.withDefaultNamespace("any_damage"));
	public static final SavedItemsConfig.SavingType ANY = SavedItemsConfig.SavingType.of(Identifier.withDefaultNamespace("any"));

	public static SavedItemsConfig.SavingType getForDamageType(DamageSource source) {
		return SavedItemsConfig.SavingType.of(
				source.typeHolder().unwrapKey().map(ResourceKey::identifier).orElse(
						Identifier.fromNamespaceAndPath("error", "unable_to_determine_damage_type_id")
				)
		);
	}

	public static Stream<SavedItemsConfig.SavingType> getDamageTypeGroupsFromType(SavedItemsConfig.SavingType type, MinecraftServer server) {
		var registry = server.overworld().damageSources().damageTypes;
		if (!registry.containsKey(type.getValue())) {
			return Stream.empty();
		}
		return Stream.concat(
				registry.get(ResourceKey.create(Registries.DAMAGE_TYPE, type.getValue())).stream().flatMap(
						entry -> entry.tags().map(SavedItemsConfig.SavingType::of)
				),
				Stream.of(ANY_DAMAGE)
		);
	}

	public static Stream<SavedItemsConfig.SavingType> getDamageTypesInGroup(SavedItemsConfig.SavingType typeGroup, MinecraftServer server) {
		var registry = server.overworld().damageSources().damageTypes;
		if (typeGroup.equals(ANY_DAMAGE)) {
			return registry.keySet().stream().map(SavedItemsConfig.SavingType::of);
		} else if (typeGroup.key().right().isPresent()) {
			return registry.get(typeGroup.key().right().get()).map(
					entryList -> entryList.stream().filter(
							entry -> entry.unwrapKey().isPresent()
					).map(entry -> SavedItemsConfig.SavingType.of(entry.unwrapKey().get().identifier()))
			).orElse(Stream.empty());
		} else {
			return Stream.empty();
		}
	}

	private void addItem(SavedItemsConfig.SavingType category, ItemStack stack) {
		try {
			for (var item : items.get(stack.getItem())) {
				if (item.components.equals(Optional.ofNullable(stack.getComponentsPatch()))) {
					if (item.typeCounts.containsKey(category)) {
						item.typeCounts.get(category).add(stack.getCount());
					} else {
						item.typeCounts.put(category, new MutableInt(stack.getCount()));
					}
					return;
				}
			}

			Map<SavedItemsConfig.SavingType, MutableInt> map = new HashMap<>();
			map.put(category, new MutableInt(stack.getCount()));
			items.put(stack.getItem(), new SavedItemEntry(Optional.ofNullable(stack.getComponentsPatch()), map));
		} finally {
			setDirty();
		}
	}

	private static final String translationKey = "lore.metacraft.dropped_by";
	private void tryAddPlayerSource(ItemStack stack, Component playerSource) {
		var dropNBTString = Component.translatableWithFallback(
				translationKey,"Dropped by " + playerSource.getString(), playerSource
		).withStyle(style -> style.withItalic(false));
		if (dropNBTString != null) {
			var lore = stack.getOrDefault(DataComponents.LORE, new ItemLore(new ArrayList<>()));
			boolean inserted = false;
			for (int i = 0; i < lore.lines().size(); i++) {
				var line = lore.lines().get(i);
				if (
						line.getContents() instanceof TranslatableContents message &&
						message.getKey().equals(translationKey)
				) {
					lore.lines().set(i, dropNBTString);
					lore.styledLines().set(i, dropNBTString);
					inserted = true;
				}
			}
			if (!inserted) {
				lore = lore.withLineAdded(dropNBTString);
			}
			stack.set(DataComponents.LORE, lore);
		}
	}
	private Optional<Component> getPlayerSource(@Nullable Entity holder) {
		if (holder instanceof ItemEntityData data) {
			return Optional.ofNullable(data.metacraft_saved_items$getSourcePlayerName());
		} else {
			return Optional.empty();
		}
	}

	public boolean tryAddItem(SavedItemsConfig.SavingType category, ItemStack stack, @Nullable Entity holder) {
		var random = server.overworld().getRandom();
		return SavedItemsConfig.getConfig().streamAllGroupsFromTypes(category, server).filter(
				save ->
					save.predicate().test(stack) &&
					random.nextDouble() <= save.probability()
		).findFirst().map(save -> {
			var newStack = stack.copy();
			if (newStack.isDamageableItem()) {
				int durability = newStack.getMaxDamage() - newStack.getDamageValue();
				int newDurability = Math.round(durability * save.damageModifier().sample(random));
				newStack.hurtAndBreak(durability - newDurability, server.overworld(), null, item -> {});
			}
			newStack.setCount(Math.round(newStack.getCount() * save.countModifier().sample(random)));
			if (newStack.isEmpty()) {
				return false;
			}
			if (newStack.has(DataComponents.CUSTOM_NAME)) {
				getPlayerSource(holder).ifPresent(source -> {
					tryAddPlayerSource(newStack, source);
				});
			}
			addItem(category, newStack);
			return true;
		}).orElse(false);
	}

	public List<ItemStack> extractItems(
			SavedItemsConfig.SavingType type, IntProvider numItemTypes, IntProvider countRange, ItemPredicate items
	) {
		List<ItemStack> stacks = new ArrayList<>();
		extractItems(type, numItemTypes, countRange, items, stack -> {
			stacks.add(stack);
			return 0;
		});
		return stacks;
	}

	private static <T> Iterable<T> iterateStream(Stream<T> stream) {
		return stream::iterator;
	}

	public void extractItems(
			SavedItemsConfig.SavingType type, IntProvider numItemTypes, IntProvider countRange, ItemPredicate items,
			ToIntFunction<ItemStack> extractor
	) {
		var random = server.overworld().getRandom();
		List<Item> keys = new ArrayList<>(this.items.keySet());
		Collections.shuffle(keys, new java.util.Random() {
			@Override
			public int nextInt(int bound) {
				return random.nextInt(bound);
			}
		});
		keys = keys.stream().limit(numItemTypes.sample(random)).toList();
		for (var item : keys) {
			var selection = (List<SavedItemEntry>) this.items.get(item);
			int count = countRange.sample(server.overworld().getRandom());
			var types = SavedItemsConfig.getConfig().streamAllTypesInGroup(type, server).collect(Collectors.toSet());
			countLoop: while (count > 0) {
				if (types.isEmpty()) break;
				var typeIt = types.iterator();
				typeChecker: while (typeIt.hasNext()) {
					var individualType = typeIt.next();
					ItemStack stack;
					SavedItemEntry selected;
					MutableInt amountAvailable;
					int countToExtract;
					var selectedSlot = random.nextInt(selection.size());
					int firstSelectedSlot = selectedSlot;
					do {
						selected = selection.get(selectedSlot);
						amountAvailable = selected.typeCounts.get(individualType);
						if (amountAvailable == null) {
							typeIt.remove();
							continue typeChecker;
						}
						countToExtract = Math.min(count, Math.min(amountAvailable.getValue(), item.getDefaultMaxStackSize()));
						stack = new ItemStack(
								BuiltInRegistries.ITEM.wrapAsHolder(item),
								countToExtract, selected.components().orElse(DataComponentPatch.EMPTY)
						);
						selectedSlot++;
						if (selectedSlot > selection.size()) {
							selectedSlot = 0;
						}
						if (selectedSlot == firstSelectedSlot) {
							typeIt.remove();
							continue typeChecker;
						}
					} while (!items.test(stack));
					var remainder = extractor.applyAsInt(stack);
					countToExtract -= remainder;
					count -= countToExtract;
					amountAvailable.subtract(countToExtract);
					setDirty();
					if (amountAvailable.getValue() <= 0) {
						selected.typeCounts.remove(individualType);
						if (selected.typeCounts.isEmpty()) {
							selection.remove(selected);
							if (selection.isEmpty()) {
								break countLoop;
							}
						}
					}
					if (remainder > 0) {
						break countLoop;
					}
				}
			}
		}
	}

	private static SavedItemsData create(MinecraftServer server) {
		return new SavedItemsData(server);
	}

	private SavedItemsData load(Multimap<Item, SavedItemEntry> items) {
		this.items.putAll(items);
		return this;
	}

	public record SavedItemEntry(Optional<DataComponentPatch> components, Map<SavedItemsConfig.SavingType, MutableInt> typeCounts) {
		public static final Codec<SavedItemEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(//Careful, this is used by a datafixer!
						DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(SavedItemEntry::components),
						Codec.unboundedMap(SavedItemsConfig.SavingType.CODEC, Codec.INT).xmap(
								map -> map.entrySet().stream().map(
										entry -> Pair.of(entry.getKey(), new MutableInt(entry.getValue()))
								).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)),
								map -> map.entrySet().stream().map(
										entry -> Pair.of(entry.getKey(), entry.getValue().getValue())
								).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
						).fieldOf("typeCounts").forGetter(SavedItemEntry::typeCounts)
				).apply(instance, SavedItemEntry::new)
		);
	}
}
