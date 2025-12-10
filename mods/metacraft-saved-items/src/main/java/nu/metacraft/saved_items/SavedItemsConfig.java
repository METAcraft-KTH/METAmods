package nu.metacraft.saved_items;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Items;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;
import nu.metacraft.lib.config.extensions.Modifiable;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SavedItemsConfig implements Modifiable {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(SavedItems.MODID + ".json");

	public static final Codec<SavedItemsConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(SavingType.CODEC, SavingEntry.CODEC.listOf()).fieldOf("saveEntries").forGetter(
					config -> config.getSaveEntries().keySet().stream().collect(
							Collectors.<SavingType, SavingType, List<SavingEntry>>toMap(
									key -> key, key -> config.getSaveEntries().get(key).stream().toList()
							)
					)
			),
			Codec.unboundedMap(SavingType.CODEC, SavingType.CODEC.listOf()).fieldOf("groups").forGetter(
					config -> config.getSaveEntries().keySet().stream().collect(
							Collectors.<SavingType, SavingType, List<SavingType>>toMap(
									key -> key, key -> config.groups.get(key).stream().toList()
							)
					)
			)
	).apply(instance, SavedItemsConfig::new));
	private static final ServerAware<ConfigContainer<SavedItemsConfig>, Loaded> config = ConfigContainer.Builder.create(
			CODEC, () -> {
				var config = new SavedItemsConfig();
				config.addGroup(SavedItemsData.ANY, SavedItemsData.ANY_DAMAGE, SavedItemsData.DESPAWN_TYPE);
				config.getSaveEntries().put(SavedItemsData.ANY, new SavingEntry(
						ObjectStorage.fromValue(
								ItemPredicate.CODEC,
								ItemPredicate.Builder.item().of(
										BuiltInRegistries.ITEM,
										Items.SHULKER_BOX, Items.BUNDLE
								).build()
						),
						1,
						ConstantFloat.of(1),
						ConstantFloat.of(1)
				));
				config.getSaveEntries().put(SavedItemsData.ANY, new SavingEntry(
						ObjectStorage.fromValue(
								ItemPredicate.CODEC,
								ItemPredicate.Builder.item().of(
										BuiltInRegistries.ITEM,
										Items.DIAMOND, Items.NETHER_STAR, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP,
										Items.NETHERITE_BLOCK, Items.DIAMOND_BLOCK, Items.ANCIENT_DEBRIS,
										Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION
								).build()
						),
						1,
						UniformFloat.of(0.75f, 1),
						UniformFloat.of(0.75f, 1)
				));
				config.getSaveEntries().put(SavedItemsData.ANY, new SavingEntry(
						ObjectStorage.fromValue(
								ItemPredicate.CODEC,
								ItemPredicate.Builder.item().withComponents(
										DataComponentMatchers.Builder.components().partial(
												DataComponentPredicates.ENCHANTMENTS,
												EnchantmentsPredicate.enchantments(
														List.of(new EnchantmentPredicate(
																Optional.empty(), MinMaxBounds.Ints.ANY
														))
												)
										).build()
								).build()
						),
						1,
						UniformFloat.of(0.75f, 1),
						UniformFloat.of(0.75f, 1)
				));
				return config;
			}
	).reloadAfterServer().buildRegistryAware(
			configPath,
			(config, server) -> Loaded.create(config.saveEntries, server.registryAccess())
	);

	private final Multimap<SavingType, SavingEntry> saveEntries;
	private final Multimap<SavingType, SavingType> groups;
	private final Multimap<SavingType, SavingType> reverseGroupLookup;

	private boolean isModified = false;

	private static <K, V> Multimap<K, V> createMultimap() {
		return MultimapBuilder.hashKeys().arrayListValues().build();
	}

	public SavedItemsConfig(
			Map<SavingType, List<SavingEntry>> saveEntries, Map<SavingType, List<SavingType>> groups
	) {
		this.saveEntries = saveEntries.entrySet().stream().collect(
				Multimaps.flatteningToMultimap(
						Map.Entry::getKey, entry -> entry.getValue().stream(),
						SavedItemsConfig::createMultimap
				)
		);
		this.groups = groups.entrySet().stream().collect(
				Multimaps.flatteningToMultimap(
						Map.Entry::getKey, entry -> entry.getValue().stream(),
						SavedItemsConfig::createMultimap
				)
		);
		this.reverseGroupLookup = this.groups.entries().stream().collect(
				Multimaps.toMultimap(
						Map.Entry::getValue, Map.Entry::getKey,
						SavedItemsConfig::createMultimap
				)
		);

	}

	public SavedItemsConfig() {
		this(new HashMap<>(), new HashMap<>());
	}

	/**
	 * Note, to get the parts inside of {@link ObjectStorage},
	 * please use {@link ServerAware#get(MinecraftServer)} instead!
	 * The main reason to use this is if you wish to modify the values.
	 * @return The save entries multimap.
	 */
	public Multimap<SavingType, SavingEntry> getSaveEntries() {
		return saveEntries;
	}

	private void addGroup(SavingType name, SavingType... entries) {
		groups.putAll(name, Arrays.asList(entries));
		for (var entry : entries) {
			reverseGroupLookup.put(entry, name);
		}
	}

	private Stream<SavingType> streamDirectGroupsWithDamageLookup(SavingType itemLossType, MinecraftServer server) {
		if (server != null) {
			var fromServer = SavedItemsData.getDamageTypeGroupsFromType(itemLossType, server);
			return Stream.concat(
					fromServer,
					reverseGroupLookup.get(itemLossType).stream()
			);
		}
		return reverseGroupLookup.get(itemLossType).stream();
	}

	private Stream<SavingType> streamTypesFromGroupWithDamageLookup(SavingType itemLossType, MinecraftServer server) {
		if (server != null) {
			var fromServer = SavedItemsData.getDamageTypesInGroup(itemLossType, server);
			return Stream.concat(
					fromServer,
					groups.get(itemLossType).stream()
			);
		}
		return groups.get(itemLossType).stream();
	}

	public Stream<SavingEntry.LoadedSavingEntry> streamAllGroupsFromTypes(SavingType itemLossType, MinecraftServer server) {
		return stream(itemLossType, server, true).flatMap(group -> config.get(server).saveEntries.get(group).stream());
	}

	public Stream<SavingType> streamAllTypesInGroup(SavingType typeGroup, MinecraftServer server) {
		return stream(typeGroup, server, false);
	}

	private Stream<SavingType> stream(SavingType start, MinecraftServer server, boolean reverse) {
		return StreamSupport.stream(
				((Iterable<SavingType>) () -> new SavingTypeIterator(start, server, reverse)).spliterator(), false
		);
	}

	@Override
	public void setModified(boolean modified) {
		isModified = modified;
	}

	@Override
	public boolean isModified() {
		return isModified;
	}

	public class SavingTypeIterator implements Iterator<SavingType> {
		private final Queue<SavingType> types = new ArrayDeque<>();
		private final MinecraftServer server;
		private final boolean reverse;

		public SavingTypeIterator(SavingType start, MinecraftServer server, boolean reverse) {
			types.add(start);
			this.reverse = reverse;
			this.server = server;
		}

		@Override
		public boolean hasNext() {
			return !types.isEmpty();
		}

		@Override
		public SavingType next() {
			var next = types.poll();
			if (reverse) {
				streamDirectGroupsWithDamageLookup(next, server).forEach(types::add);
			} else {
				streamTypesFromGroupWithDamageLookup(next, server).forEach(types::add);
			}
			return next;
		}
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static SavedItemsConfig getConfig() {
		return SavedItemsConfig.config.getContainer().get();
	}

	public record SavingEntry(
			ObjectStorage<ItemPredicate> predicate, double probability,
			FloatProvider damageModifier, FloatProvider countModifier
	) {
		public static final Codec<SavingEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ObjectStorage.createCodec(ItemPredicate.CODEC).fieldOf("predicate").forGetter(SavingEntry::predicate),
						Codec.DOUBLE.fieldOf("probability").forGetter(SavingEntry::probability),
						FloatProvider.CODEC.fieldOf("damageModifier").forGetter(SavingEntry::damageModifier),
						FloatProvider.CODEC.fieldOf("countModifier").forGetter(SavingEntry::countModifier)
				).apply(instance, SavingEntry::new)
		);

		public Optional<LoadedSavingEntry> load(HolderLookup.Provider lookup) {
			return predicate.parse(lookup).resultOrPartial(
					SavedItems.LOGGER::error
			).map(
				predicate -> new LoadedSavingEntry(
						predicate, probability, damageModifier, countModifier
				)
			);
		}

		public record LoadedSavingEntry(
				ItemPredicate predicate, double probability,
				FloatProvider damageModifier, FloatProvider countModifier
		) {}
	}

	public record Loaded(Multimap<SavingType, SavingEntry.LoadedSavingEntry> saveEntries) {

		public static Loaded create(
				Multimap<SavingType, SavingEntry> saveEntries, HolderLookup.Provider lookup
		) {
			return new Loaded(
					saveEntries.entries().stream().map(
							entry -> entry.getValue().load(lookup).map(
									value -> Pair.of(
											entry.getKey(), value
									)
							).orElse(null)
					).filter(Objects::nonNull).collect(
							Multimaps.toMultimap(Pair::getFirst, Pair::getSecond, SavedItemsConfig::createMultimap)
					)
			);
		}

	}

	public record SavingType(Either<Identifier, TagKey<DamageType>> key) {
		public static final Codec<SavingType> CODEC = Codec.either(
				Identifier.CODEC, TagKey.hashedCodec(Registries.DAMAGE_TYPE)
		).xmap(
				SavingType::new, SavingType::key
		);
		public static SavingType of(Identifier id) {
			return new SavingType(Either.left(id));
		}

		public static SavingType of(TagKey<DamageType> tag) {
			return new SavingType(Either.right(tag));
		}

		public Identifier getValue() {
			return key.map(id -> id, TagKey::location);
		}

		public static SavingType fromString(String name) {
			return CODEC.parse(JavaOps.INSTANCE, name).resultOrPartial(
					SavedItems.LOGGER::error
			).orElse(null);
		}

		@Override
		public String toString() {
			return CODEC.encodeStart(JavaOps.INSTANCE, this).resultOrPartial(
					SavedItems.LOGGER::error
			).map(o -> (String) o).orElse("ERROR");
		}
	}

}
