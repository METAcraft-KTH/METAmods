import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import nu.metacraft.lib.util.helper.TestHelper;

public class EyeLockTests {

	static void registerTests() {
		NetherRiftsTests.register("eye/table_locked", ctx -> {
			ctx.assertTrue(craftAtTable(ctx).isEmpty(), "an eye of ender was crafted in rift mode");
			ctx.succeed();
		});
		NetherRiftsTests.register("eye/table_unlocked", ctx -> {
			ctx.assertTrue(craftAtTable(ctx).is(Items.ENDER_EYE), "no eye of ender after stabilising");
			ctx.succeed();
		});
		NetherRiftsTests.register("eye/crafter_locked", ctx -> {
			var crafter = crafterWithEyeRecipe(ctx);
			ctx.runAtTickTime(15, () -> {
				ctx.assertTrue(ctx.getEntities(net.minecraft.world.entity.EntityTypes.ITEM).stream()
						.noneMatch(item -> item.getItem().is(Items.ENDER_EYE)), "the crafter made an eye of ender in rift mode");
				ctx.succeed();
			});
			ctx.pulseRedstone(crafter.below(), 2);
		});
		NetherRiftsTests.register("eye/other_recipes_untouched", ctx -> {
			var player = TestHelper.addMockPlayer(ctx);
			player.setGameMode(GameType.SURVIVAL);
			var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(ctx.getLevel(), ctx.absolutePos(new BlockPos(2, 1, 2))));
			menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
			ctx.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "planks no longer craft: " + menu.getSlot(0).getItem());
			ctx.getLevel().getServer().getPlayerList().remove(player);
			ctx.succeed();
		});
	}

	/** Ender pearl + blaze powder in a crafting table's grid; what the result slot shows. */
	static ItemStack craftAtTable(GameTestHelper ctx) {
		var player = TestHelper.addMockPlayer(ctx);
		player.setGameMode(GameType.SURVIVAL);
		var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(ctx.getLevel(), ctx.absolutePos(new BlockPos(2, 1, 2))));
		menu.getSlot(1).set(new ItemStack(Items.ENDER_PEARL));
		menu.getSlot(2).set(new ItemStack(Items.BLAZE_POWDER));
		var result = menu.getSlot(0).getItem().copy();
		ctx.getLevel().getServer().getPlayerList().remove(player);
		return result;
	}

	/** A crafter facing east holding the eye recipe; returns its relative position. */
	static BlockPos crafterWithEyeRecipe(GameTestHelper ctx) {
		var pos = new BlockPos(4, 2, 4);
		ctx.setBlock(pos, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, net.minecraft.core.FrontAndTop.EAST_UP));
		var crafter = (CrafterBlockEntity) ctx.getLevel().getBlockEntity(ctx.absolutePos(pos));
		crafter.setItem(0, new ItemStack(Items.ENDER_PEARL));
		crafter.setItem(1, new ItemStack(Items.BLAZE_POWDER));
		return pos;
	}
}
