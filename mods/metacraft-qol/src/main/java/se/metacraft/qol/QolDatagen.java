package se.metacraft.qol;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import se.metacraft.qol.silence_mobs.SilenceMobs;
import se.metacraft.qol.void_anchor.VoidAnchorBlocks;
import se.metacraft.qol.void_anchor.VoidAnchorItems;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class QolDatagen implements DataGeneratorEntrypoint {

	public static final ResourceKey<Recipe<?>> VOID_ANCHOR_RECIPE = ResourceKey.create(
			Registries.RECIPE, Qol.getID("void_anchor")
	);

	public static final ResourceKey<Recipe<?>> ECHO_FORK_RECIPE = ResourceKey.create(
			Registries.RECIPE, Qol.getID("echo_fork")
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
					// a tuning fork: sculk prongs on an echo shard, an iron handle
					shaped(RecipeCategory.TOOLS, SilenceMobs.ECHO_FORK)
							.define('S', Items.SCULK)
							.define('E', Items.ECHO_SHARD)
							.define('I', Items.IRON_INGOT)
							.pattern("S S")
							.pattern(" E ")
							.pattern(" I ")
							.unlockedBy("has_echo_shard", has(Items.ECHO_SHARD))
							.save(output, ECHO_FORK_RECIPE);
				}
			};
		}

		@Override
		public @NonNull String getName() {
			return "QolRecipes";
		}
	}

}
