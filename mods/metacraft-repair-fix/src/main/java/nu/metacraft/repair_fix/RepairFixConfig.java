package nu.metacraft.repair_fix;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class RepairFixConfig {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(RepairFix.modid + ".json");

	public static final Codec<RepairFixConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_repair_cost").forGetter(c -> c.maxRepairConst),
					Codec.BOOL.fieldOf("cap_at_max_level").forGetter(c -> c.capAtMaxLevel),
					ObjectStorage.createCodec(RepairEntry.CODEC.listOf()).fieldOf("repair_item_cost_balancing").forGetter(c -> c.repairItemCostBalancing),
					ObjectStorage.createCodec(EnchantmentEntry.CODEC.listOf()).fieldOf("enchantment_cost_combine_overrides").forGetter(c -> c.enchantmentCombineCostOverrides),
					BaseCostIncreaseMode.CODEC.fieldOf("base_cost_increase_mode").forGetter(c -> c.baseCostIncreaseMode)
			).apply(instance, RepairFixConfig::new)
	);

	private static final ServerAware<ConfigContainer<RepairFixConfig>, Loaded> CONTAINER = ConfigContainer.Builder.create(
			CODEC, RepairFixConfig::createDefault
	).buildRegistryAware(
			configPath,
			(config, server) -> Loaded.create(
					config.repairItemCostBalancing,
					config.enchantmentCombineCostOverrides,
					server.registryAccess()
			)
	);

	public static void init() {
		getConfig();
	}

	public static RepairFixConfig getConfig() {
		return CONTAINER.getContainer().get();
	}

	public static Loaded getConfig(MinecraftServer server) {
		return CONTAINER.get(server);
	}

	private final Optional<Integer> maxRepairConst;

	private final boolean capAtMaxLevel;

	private final ObjectStorage<List<RepairEntry>> repairItemCostBalancing;

	private final ObjectStorage<List<EnchantmentEntry>> enchantmentCombineCostOverrides;

	private BaseCostIncreaseMode baseCostIncreaseMode = BaseCostIncreaseMode.ENCHANTING_ONLY;

	public RepairFixConfig(
			Optional<Integer> maxRepairConst, boolean capAtMaxLevel,
			ObjectStorage<List<RepairEntry>> repairItemCostBalancing,
			ObjectStorage<List<EnchantmentEntry>> enchantmentCombineCostOverrides,
			BaseCostIncreaseMode baseCostIncreaseMode
	) {
		this.maxRepairConst = maxRepairConst;
		this.capAtMaxLevel = capAtMaxLevel;
		this.repairItemCostBalancing = repairItemCostBalancing;
		this.enchantmentCombineCostOverrides = enchantmentCombineCostOverrides;
		this.baseCostIncreaseMode = baseCostIncreaseMode;
	}

	public boolean capAtMaxLevel() {
		return capAtMaxLevel;
	}

	public int getMaxRepairCost() {
		return maxRepairConst.orElse(Integer.MAX_VALUE);
	}

	public BaseCostIncreaseMode baseCostIncreaseMode() {
		return baseCostIncreaseMode;
	}

	private static RepairFixConfig createDefault() {
		return new RepairFixConfig(
				Optional.of(40), true,
				ObjectStorage.fromValue(
						RepairEntry.CODEC.listOf(), new ArrayList<>(
								ImmutableList.of(
										new RepairEntry(
												ItemPredicate.Builder.item().of(
														BuiltInRegistries.ITEM,
														Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
														Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
														Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
														Items.NETHERITE_SWORD, Items.NETHERITE_SHOVEL,
														Items.NETHERITE_HOE
												).build(),
												ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, Items.NETHERITE_INGOT).build(),
												1
										),
										new RepairEntry(
												ItemPredicate.Builder.item().of(
														BuiltInRegistries.ITEM,
														Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
														Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
														Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
														Items.NETHERITE_SWORD, Items.NETHERITE_SHOVEL,
														Items.NETHERITE_HOE
												).build(),
												ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, Items.NETHERITE_SCRAP).build(),
												4
										)
								)
						)
				),
				ObjectStorage.fromValueWithDefaultOps(
						EnchantmentEntry.CODEC.listOf(), lookup -> new ArrayList<>(
								ImmutableList.of(
										new EnchantmentEntry(
												HolderSet.direct(lookup.getOrThrow(Enchantments.MENDING)),
												HolderSet.direct(lookup.getOrThrow(Enchantments.INFINITY)),
												10
										)
								)
						)
				),
				BaseCostIncreaseMode.ENCHANTING_ONLY
		);
	}

	public record RepairEntry(ItemPredicate tool, ItemPredicate material, int amountToFull) {
		public static final Codec<RepairEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ItemPredicate.CODEC.fieldOf("tool").forGetter(RepairEntry::tool),
						ItemPredicate.CODEC.fieldOf("material").forGetter(RepairEntry::material),
						ExtraCodecs.NON_NEGATIVE_INT.fieldOf("amount_to_full").forGetter(RepairEntry::amountToFull)
				).apply(instance, RepairEntry::new)
		);
	}

	public record EnchantmentEntry(HolderSet<Enchantment> first, HolderSet<Enchantment> second, int combineCost) {
		public static final Codec<EnchantmentEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).fieldOf("first").forGetter(EnchantmentEntry::first),
						RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).fieldOf("second").forGetter(EnchantmentEntry::second),
						ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cost").forGetter(EnchantmentEntry::combineCost)
				).apply(instance, EnchantmentEntry::new)
		);
	}


	public enum BaseCostIncreaseMode implements StringRepresentable {
		DEFAULT("default"),
		ENCHANTING_ONLY("enchanting_only"),
		NONE("none");

		private final String name;

		public static final Codec<BaseCostIncreaseMode> CODEC = StringRepresentable.fromEnum(BaseCostIncreaseMode::values);

		BaseCostIncreaseMode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}


	public static class Loaded {

		private final List<RepairEntry> repairItemCostBalancing;
		private final List<EnchantmentEntry> enchantmentCombineCostOverrides;

		protected Loaded(List<RepairEntry> repairItemCostBalancing, List<EnchantmentEntry> enchantmentCombineCostOverrides) {
			this.repairItemCostBalancing = repairItemCostBalancing;
			this.enchantmentCombineCostOverrides = enchantmentCombineCostOverrides;
		}

		public OptionalInt findRepairCount(ItemStack tool, ItemStack material) {
			for (var entry : repairItemCostBalancing) {
				if (entry.tool.test(tool) && entry.material.test(material)) {
					return OptionalInt.of(entry.amountToFull);
				}
			}
			return OptionalInt.empty();
		}

		public OptionalInt findCombineCost(Holder<Enchantment> first, Holder<Enchantment> second) {
			for (var entry : enchantmentCombineCostOverrides) {
				if ((entry.first.contains(first) && entry.second.contains(second)) || (entry.first.contains(second) && entry.second.contains(first))) {
					return OptionalInt.of(entry.combineCost);
				}
			}
			return OptionalInt.empty();
		}

		public static Loaded create(
				ObjectStorage<List<RepairEntry>> repairItemCostBalancing,
				ObjectStorage<List<EnchantmentEntry>> enchantmentCombineCostOverrides,
				HolderLookup.Provider lookup
		) {
			return new Loaded(
					repairItemCostBalancing.parse(lookup).resultOrPartial(RepairFix.getLogger()::error).orElse(new ArrayList<>()),
					enchantmentCombineCostOverrides.parse(lookup).resultOrPartial(RepairFix.getLogger()::error).orElse(new ArrayList<>())
			);
		}

	}
}
