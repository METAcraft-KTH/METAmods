package nu.metacraft.qol;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import nu.metacraft.qol.silence_mobs.SilenceMobs;
import nu.metacraft.qol.void_anchor.VoidAnchorBlocks;
import nu.metacraft.qol.void_anchor.VoidAnchorItems;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class QolDatagen implements DataGeneratorEntrypoint {

	public static final ResourceKey<Recipe<?>> VOID_ANCHOR_RECIPE = ResourceKey.create(
			Registries.RECIPE, Qol.getID("void_anchor")
	);

	public static final ResourceKey<Recipe<?>> MUFFLER_RECIPE = ResourceKey.create(
			Registries.RECIPE, Qol.getID("muffler")
	);

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(Recipes::new);
		pack.addProvider(LootTableProvider::new);
		pack.addProvider(BlockTagsProvider::new);
	}

	public static class BlockTagsProvider extends FabricTagsProvider.BlockTagsProvider {

		public BlockTagsProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			builder(BlockTags.MINEABLE_WITH_PICKAXE).add(VoidAnchorBlocks.VOID_ANCHOR.key());
		}
	}

	public static class LootTableProvider extends FabricBlockLootSubProvider {

		protected LootTableProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(dataOutput, registryLookup);
		}

		@Override
		public void generate() {
			dropSelf(VoidAnchorBlocks.VOID_ANCHOR.value());
		}
	}

	public static class Recipes extends FabricRecipeProvider {

		public Recipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, registriesFuture);
		}

		@Override
		protected RecipeProvider createRecipeProvider(
				HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements
		) {
			return new RecipeProvider(recipes, advancements) {
				@Override
				public void buildRecipes() {
					shaped(RecipeCategory.MISC, VoidAnchorItems.VOID_ANCHOR)
							.define('C', Items.CRYING_OBSIDIAN)
							.define('E', Items.ENDER_EYE)
							.pattern("CCC")
							.pattern("EEE")
							.pattern("CCC")
							.unlockedBy("has_crying_obsidian", has(Items.CRYING_OBSIDIAN))
							.save(output, VOID_ANCHOR_RECIPE);
					shapeless(RecipeCategory.TOOLS, SilenceMobs.MUFFLER)
							.requires(ItemTags.WOOL)
							.requires(Items.AMETHYST_SHARD)
							.requires(Items.STRING)
							.unlockedBy("has_amethyst_shard", has(Items.AMETHYST_SHARD))
							.save(output, MUFFLER_RECIPE);
				}
			};
		}

		@Override
		public @NonNull String getName() {
			return "QolRecipes";
		}
	}

}
