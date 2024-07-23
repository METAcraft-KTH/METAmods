package se.datasektionen.mc.saved_items.item_saving;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.PersistentState;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.saved_items.SavedItemsConfig;
import se.datasektionen.mc.saved_items.SavedItems;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SavedItemsData extends PersistentState {

	private static final String key = SavedItems.MODID;
	private static final String ITEMS = "Items";
	private static PersistentState.Type<SavedItemsData> getType(MinecraftServer server) {
		return new Type<>(
				() -> create(server), (nbt, wrapper) -> load(server, nbt, wrapper), null
		);
	}

	public static SavedItemsData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), key);
	}

	private final Multimap<Item, SavedItemEntry> items = MultimapBuilder.hashKeys().arrayListValues().build();

	private final MinecraftServer server;

	private SavedItemsData(MinecraftServer server) {
		this.server = server;
	}

	public static final SavedItemsConfig.SavingType DESPAWN_TYPE = SavedItemsConfig.SavingType.of(Identifier.ofVanilla("despawn"));
	public static final SavedItemsConfig.SavingType ANY_DAMAGE = SavedItemsConfig.SavingType.of(Identifier.ofVanilla("any_damage"));
	public static final SavedItemsConfig.SavingType ANY = SavedItemsConfig.SavingType.of(Identifier.ofVanilla("any"));

	public static SavedItemsConfig.SavingType getForDamageType(DamageSource source) {
		return SavedItemsConfig.SavingType.of(
				source.getTypeRegistryEntry().getKey().map(RegistryKey::getValue).orElse(
						Identifier.of("error", "unable_to_determine_damage_type_id")
				)
		);
	}

	public static Stream<SavedItemsConfig.SavingType> getDamageTypeGroupsFromType(SavedItemsConfig.SavingType type, MinecraftServer server) {
		var registry = server.getOverworld().getDamageSources().registry;
		if (!registry.containsId(type.getValue())) {
			return Stream.empty();
		}
		return Stream.concat(
				registry.getEntry(RegistryKey.of(RegistryKeys.DAMAGE_TYPE, type.getValue())).stream().flatMap(
						entry -> entry.streamTags().map(SavedItemsConfig.SavingType::of)
				),
				Stream.of(ANY_DAMAGE)
		);
	}

	public static Stream<SavedItemsConfig.SavingType> getDamageTypesInGroup(SavedItemsConfig.SavingType typeGroup, MinecraftServer server) {
		var registry = server.getOverworld().getDamageSources().registry;
		if (typeGroup.equals(ANY_DAMAGE)) {
			return registry.getIds().stream().map(SavedItemsConfig.SavingType::of);
		} else if (typeGroup.key().right().isPresent()) {
			return registry.getEntryList(typeGroup.key().right().get()).map(
					entryList -> entryList.stream().filter(
							entry -> entry.getKey().isPresent()
					).map(entry -> SavedItemsConfig.SavingType.of(entry.getKey().get().getValue()))
			).orElse(Stream.empty());
		} else {
			return Stream.empty();
		}
	}

	private void addItem(SavedItemsConfig.SavingType category, ItemStack stack) {
		try {
			for (var item : items.get(stack.getItem())) {
				if (item.components.equals(Optional.ofNullable(stack.getComponentChanges()))) {
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
			items.put(stack.getItem(), new SavedItemEntry(Optional.ofNullable(stack.getComponentChanges()), map));
		} finally {
			markDirty();
		}
	}

	private static final String translationKey = "lore.metacraft.dropped_by";
	private void tryAddPlayerSource(ItemStack stack, Text playerSource) {
		var dropNBTString = Text.translatableWithFallback(
				translationKey,"Dropped by " + playerSource.getString(), playerSource
		).styled(style -> style.withItalic(false));
		if (dropNBTString != null) {
			var lore = stack.getOrDefault(DataComponentTypes.LORE, new LoreComponent(new ArrayList<>()));
			boolean inserted = false;
			for (int i = 0; i < lore.lines().size(); i++) {
				var line = lore.lines().get(i);
				if (
						line.getContent() instanceof TranslatableTextContent message &&
						message.getKey().equals(translationKey)
				) {
					lore.lines().set(i, dropNBTString);
					lore.styledLines().set(i, dropNBTString);
					inserted = true;
				}
			}
			if (!inserted) {
				lore = lore.with(dropNBTString);
			}
			stack.set(DataComponentTypes.LORE, lore);
		}
	}
	private Optional<Text> getPlayerSource(ItemStack stack) {
		if (stack.getHolder() instanceof ItemEntityData data) {
			return Optional.ofNullable(data.metacraft_saved_items$getSourcePlayerName());
		} else {
			return Optional.empty();
		}
	}

	public boolean tryAddItem(SavedItemsConfig.SavingType category, ItemStack stack) {
		var random = server.getOverworld().getRandom();
		return SavedItemsConfig.getConfig().streamAllGroupsFromTypes(category, server).filter(
				save ->
					save.predicate().test(stack) &&
					random.nextDouble() <= save.probability()
		).findFirst().map(save -> {
			var newStack = stack.copy();
			if (newStack.isDamageable()) {
				int durability = newStack.getMaxDamage() - newStack.getDamage();
				int newDurability = Math.round(durability * save.damageModifier().get(random));
				newStack.damage(durability - newDurability, server.getOverworld(), null, item -> {});
			}
			newStack.setCount(Math.round(newStack.getCount() * save.countModifier().get(random)));
			if (newStack.isEmpty()) {
				return false;
			}
			if (newStack.contains(DataComponentTypes.CUSTOM_NAME)) {
				getPlayerSource(stack).ifPresent(source -> {
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
		var random = server.getOverworld().getRandom();
		List<Item> keys = new ArrayList<>(this.items.keySet());
		Collections.shuffle(keys, new java.util.Random() {
			@Override
			public int nextInt(int bound) {
				return random.nextInt(bound);
			}
		});
		keys = keys.stream().limit(numItemTypes.get(random)).toList();
		for (var item : keys) {
			var selection = (List<SavedItemEntry>) this.items.get(item);
			int count = countRange.get(server.getOverworld().getRandom());
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
						countToExtract = Math.min(count, Math.min(amountAvailable.getValue(), item.getMaxCount()));
						stack = new ItemStack(
								Registries.ITEM.getEntry(item),
								countToExtract, selected.components().orElse(ComponentChanges.EMPTY)
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
					markDirty();
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

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound entryLists = new NbtCompound();
		for (var item : items.keySet()) {
			NbtList items = new NbtList();
			for (var itemEntry : this.items.get(item)) {
				SavedItemEntry.CODEC.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), itemEntry).resultOrPartial(
						SavedItems.LOGGER::error
				).ifPresent(items::add);
			}
			entryLists.put(Registries.ITEM.getId(item).toString(), items);
		}
		nbt.put(ITEMS, entryLists);
		return nbt;
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
		var items = nbt.getCompound(ITEMS);
		for (var itemID : items.getKeys()) {
			Optional.ofNullable(Identifier.tryParse(itemID)).map(Registries.ITEM::get).ifPresentOrElse(item -> {
				for (var itemEntry : items.getList(itemID, NbtElement.COMPOUND_TYPE)) {
					SavedItemEntry.CODEC.parse(wrapper.getOps(NbtOps.INSTANCE), itemEntry).resultOrPartial(
							SavedItems.LOGGER::error
					).ifPresent(entry -> {
						this.items.put(item, entry);
					});
				}
			}, () -> {
				SavedItems.LOGGER.error("Unable to parse item: " + itemID);
			});
		}
	}

	private static SavedItemsData create(MinecraftServer server) {
		return new SavedItemsData(server);
	}

	private static SavedItemsData load(MinecraftServer server, NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
		var data = new SavedItemsData(server);
		data.readNBT(nbt, wrapper);
		return data;
	}

	public record SavedItemEntry(Optional<ComponentChanges> components, Map<SavedItemsConfig.SavingType, MutableInt> typeCounts) {
		public static final Codec<SavedItemEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ComponentChanges.CODEC.optionalFieldOf("components").forGetter(SavedItemEntry::components),
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
