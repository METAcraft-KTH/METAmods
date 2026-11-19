package se.metacraft.playertrading.gametest;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.jspecify.annotations.NonNull;
import org.pcollections.TreePVector;
import se.metacraft.playertrading.block.TradingBlocks;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.shop.Shop;
import se.metacraft.playertrading.shop.ShopType;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class ShopSameTickDupeTest implements CustomTestMethodInvoker {

	private static final BlockPos BARREL = new BlockPos(1, 1, 1);
	private static final int STOCK = 10;
	private static final int REMOVED = 9;

	private static Shop.SimpleOffer diamondForDirt() {
		return new Shop.SimpleOffer(
			new Shop.SimpleOffer.Price(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIRT)), Optional.empty()),
			ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIAMOND))
		);
	}

	@GameTest
	public void stockRemovedInSameTickAsTrade(GameTestHelper helper) {
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.above(), TradingBlocks.SHOP.value());
		var barrel = helper.getBlockEntity(BARREL, BarrelBlockEntity.class);
		barrel.setItem(0, new ItemStack(Items.DIAMOND, STOCK));

		var shop = helper.getBlockEntity(BARREL.above(), ShopBlockEntity.class);
		var data = Shop.create(new ShopType.Player(Optional.of(UUID.randomUUID()))).setOffers(TreePVector.singleton(diamondForDirt()));
		shop.applyComponents(DataComponentMap.builder().set(TradingComponents.SHOP, data).build(), DataComponentPatch.EMPTY);

		var buyer = helper.makeMockServerPlayerInLevel();
		shop.onUse(buyer, helper.absolutePos(BARREL.above()));
		var menu = buyer.containerMenu;

		barrel.getItem(0).shrink(REMOVED);
		barrel.setChanged();
		menu.getSlot(0).set(new ItemStack(Items.DIRT, STOCK));
		menu.clicked(2, 0, ContainerInput.QUICK_MOVE, buyer);

		int bought = buyer.getInventory().countItem(Items.DIAMOND);
		int sold = STOCK - REMOVED - barrel.countItem(Items.DIAMOND);
		if (bought != sold) {
			helper.fail("Expected " + sold + " diamonds bought, but found " + bought);
		}
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(
		@NonNull GameTestHelper helper, @NonNull Method method
	) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

}
