import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class ConcreteCauldronTests {

	static void register(String name, Consumer<GameTestHelper> test) {
		QolTests.register("concrete_cauldron/" + name, test);
	}

	static BlockState water(int level) {
		return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
	}

	/** A cauldron at (x, 1, 1) in the test's space. */
	static BlockPos cauldron(GameTestHelper ctx, int x, BlockState state) {
		var pos = ctx.absolutePos(new BlockPos(x, 1, 1));
		ctx.getLevel().setBlockAndUpdate(pos, state);
		return pos;
	}

	/** Drops a still stack into the cauldron, low enough to be in the water at any level. */
	static ItemEntity dropIn(GameTestHelper ctx, BlockPos cauldron, ItemStack stack) {
		var at = Vec3.atBottomCenterOf(cauldron).add(0, 0.3, 0);
		var item = new ItemEntity(ctx.getLevel(), at.x, at.y, at.z, stack, 0, 0, 0);
		ctx.getLevel().addFreshEntity(item);
		return item;
	}

	static void expectItem(GameTestHelper ctx, ItemEntity entity, Item item, int count) {
		var stack = entity.getItem();
		ctx.assertTrue(stack.is(item) && stack.getCount() == count, "the stack is " + stack + ", expected " + count + " " + item);
	}

	static void expectBlock(GameTestHelper ctx, BlockPos pos, BlockState state) {
		var actual = ctx.getLevel().getBlockState(pos);
		ctx.assertTrue(actual.equals(state), "the cauldron is " + actual + ", expected " + state);
	}

	/** Registers the concrete cauldron's game tests; {@link QolTests} runs them with every other feature's. */
	static void registerTests() {
		register("stack_converts", ctx -> {
			var pos = cauldron(ctx, 1, water(3));
			var item = dropIn(ctx, pos, new ItemStack(Items.CONCRETE_POWDER.white(), 64));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.CONCRETE.white(), 64);
				expectBlock(ctx, pos, water(2));
				ctx.succeed();
			});
		});
		register("single_item_costs_a_level", ctx -> {
			var pos = cauldron(ctx, 1, water(3));
			var item = dropIn(ctx, pos, new ItemStack(Items.CONCRETE_POWDER.white(), 1));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.CONCRETE.white(), 1);
				expectBlock(ctx, pos, water(2));
				ctx.succeed();
			});
		});
		register("last_level_empties", ctx -> {
			var pos = cauldron(ctx, 1, water(1));
			var item = dropIn(ctx, pos, new ItemStack(Items.CONCRETE_POWDER.white(), 16));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.CONCRETE.white(), 16);
				expectBlock(ctx, pos, Blocks.CAULDRON.defaultBlockState());
				ctx.succeed();
			});
		});
		register("colour_matches", ctx -> {
			var pos = cauldron(ctx, 1, water(3));
			var item = dropIn(ctx, pos, new ItemStack(Items.CONCRETE_POWDER.red(), 8));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.CONCRETE.red(), 8);
				ctx.succeed();
			});
		});
		register("other_items_untouched", ctx -> {
			var pos = cauldron(ctx, 1, water(3));
			var item = dropIn(ctx, pos, new ItemStack(Items.STONE, 10));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.STONE, 10);
				expectBlock(ctx, pos, water(3));
				ctx.succeed();
			});
		});
		register("dry_cauldrons_do_nothing", ctx -> {
			var empty = cauldron(ctx, 0, Blocks.CAULDRON.defaultBlockState());
			var lava = cauldron(ctx, 2, Blocks.LAVA_CAULDRON.defaultBlockState());
			var snowState = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
			var snow = cauldron(ctx, 4, snowState);
			var inEmpty = dropIn(ctx, empty, new ItemStack(Items.CONCRETE_POWDER.white(), 4));
			var inSnow = dropIn(ctx, snow, new ItemStack(Items.CONCRETE_POWDER.white(), 4));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, inEmpty, Items.CONCRETE_POWDER.white(), 4);
				expectItem(ctx, inSnow, Items.CONCRETE_POWDER.white(), 4);
				expectBlock(ctx, snow, snowState);
				expectBlock(ctx, lava, Blocks.LAVA_CAULDRON.defaultBlockState());
				ctx.succeed();
			});
		});
		register("off_no_conversion", ctx -> {
			var pos = cauldron(ctx, 1, water(3));
			var item = dropIn(ctx, pos, new ItemStack(Items.CONCRETE_POWDER.white(), 64));
			ctx.runAtTickTime(10, () -> {
				expectItem(ctx, item, Items.CONCRETE_POWDER.white(), 64);
				expectBlock(ctx, pos, water(3));
				ctx.succeed();
			});
		});
	}

}
