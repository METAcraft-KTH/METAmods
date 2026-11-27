package se.metacraft.portable_jukebox;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableSubProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetContainerContents;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import org.jspecify.annotations.NonNull;
import se.metacraft.portable_jukebox.item.BoomboxItems;
import se.metacraft.portable_jukebox.item.components.BoomboxComponents;
import se.metacraft.portable_jukebox.item.components.BoomboxConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class BoomboxDatagen implements DataGeneratorEntrypoint {

	@Override
	public void onInitializeDataGenerator(@NonNull FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(EntityLootTableGen::new);
		pack.addProvider(BlockTagsProvider::new);
	}

	public static class BlockTagsProvider extends FabricTagsProvider.BlockTagsProvider {

		public BlockTagsProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			builder(BlockTags.MINEABLE_WITH_AXE).add(ResourceKey.create(Registries.BLOCK, Boombox.getID("portable_jukebox")));
		}
	}

	public static class EntityLootTableGen extends SimpleFabricLootTableSubProvider {

		private static final int VOLUME_RESOLUTION = 5;
		private static final int SPEED_RESOLUTION = 20;

		public static final ResourceKey<LootTable> PIGLIN_BOOMBOX_DROP = key("piglin_drop");

		private final Map<ResourceKey<LootTable>, LootTable.Builder> map = new HashMap<>();

		private final Supplier<HolderLookup.Provider> lookup;

		public EntityLootTableGen(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
			super(output, registryLookupFuture, LootContextParamSets.ENTITY);
			this.lookup = Suppliers.memoize(registryLookupFuture::join);
		}

		private float getAmountLinear(int currentRes, int maxRes, float minAmount, float maxAmount) {
			float amount = maxAmount - minAmount;
			return minAmount + amount * currentRes / maxRes;
		}

		private float getAmountLog10(int currentRes, int maxRes, float minAmount, float maxAmount) {
			float amount = maxAmount - minAmount;
			return (float) (minAmount + amount * Math.log10(currentRes+1) / Math.log10(maxRes+1));
		}

		private <T extends UniformContainerBase.Builder<T>> UniformContainerBase.Builder<T> applyBoomboxData(UniformContainerBase.Builder<T> builder) {
			return builder.apply(
				SetContainerContents.setContents(BoomboxComponents.Manipulators.BOOMBOX).withEntry(
					NestedLootTable.inlineLootTable(
						LootTable.lootTable().withPool(
							LootPool.lootPool().setRolls(
								Holder.direct(new ConstantValue(1))
							).add(
								TagEntry.expandTag(lookup.get().getOrThrow(ItemTags.CREEPER_DROP_MUSIC_DISCS)).setWeight(1).when(
									LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
										lookup.get().lookupOrThrow(Registries.ENCHANTMENT), 0.05f, 0.1f
									)
								)
							).add(
								LootItem.lootTableItem(Items.MUSIC_DISC_PIGSTEP).setWeight(15).when(
									LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
										lookup.get().lookupOrThrow(Registries.ENCHANTMENT), 0.05f, 0.1f
									)
								)
							)
						).build()
					)
				)
			);
		}

		private void generateBoomboxes(
			LootPool.Builder pool, int weightPerEntry, int qualityPerEntry
		) {
			for (int v = 0; v < VOLUME_RESOLUTION; v++) {
				float volume = getAmountLinear(v, VOLUME_RESOLUTION, 0.5f, 1);
				for (int p = 0; p < SPEED_RESOLUTION; p++) {
					float speed = getAmountLog10(p, SPEED_RESOLUTION, 0.5f, 2.0f);
					var components = DataComponentPatch.builder().set(
						BoomboxComponents.BOOMBOX_CONFIGURATION,
						new BoomboxConfiguration(
							volume, speed
						)
					);
					var builder = LootItem.lootTableItem(BoomboxItems.BOOMBOX).setWeight(weightPerEntry).setQuality(qualityPerEntry).apply(
						Holder.direct(
							new SetComponentsFunction(
								Optional.empty(),
								components.build()
							)
						)
					);
					pool.add(applyBoomboxData(builder));
				}
			}
		}

		private void generate() {
			var table = LootTable.lootTable();
			var pool = LootPool.lootPool().setRolls(
				Holder.direct(new ConstantValue(1))
			);
			generateBoomboxes(pool,1, 1);
			pool.add(
				applyBoomboxData(
					LootItem.lootTableItem(BoomboxItems.BOOMBOX).setQuality(
						VOLUME_RESOLUTION*SPEED_RESOLUTION
					).setWeight(VOLUME_RESOLUTION*SPEED_RESOLUTION)
				)
			);
			pool.when(
				LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
					lookup.get().lookupOrThrow(Registries.ENCHANTMENT), 0.05f, 0.1f
				)
			);
			table.withPool(pool);
			add(PIGLIN_BOOMBOX_DROP, table);
		}

		private static ResourceKey<LootTable> key(String name) {
			return ResourceKey.create(Registries.LOOT_TABLE, Boombox.getID(name));
		}

		private void add(ResourceKey<LootTable> key, LootTable.Builder lootTable) {
			map.put(key, lootTable);
		}

		@Override
		public void generate(@NonNull BiConsumer<ResourceKey<LootTable>, LootTable.Builder> biConsumer) {
			generate();
			map.forEach(biConsumer);
		}

		@Override
		public void run() {
			generate();
		}
	}
}
