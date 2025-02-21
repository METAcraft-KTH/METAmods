import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestFunction;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.path.SymlinkValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.TestHelper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class TestPlayerData {

	@BeforeAll
	public static void init() {
		TestHelper.init(METAcraftLib::new);
	}

	private Entity getNewEntity(TestContext ctx, Entity oldEntity) {
		var entity = ctx.getWorld().getEntity(oldEntity.getUuid());
		ctx.assertTrue(entity != null, oldEntity + " went missing!");
		return entity;
	}

	@Test
	public void testPlayerDataMap() throws IOException, SymlinkValidationException, InterruptedException {
		TestHelper.runTestServer(List.of(
				new TestFunction(
						"player-data-test", "save-and-load", "empty",
						1, 1, true,
						ctx -> {
							var player = TestHelper.addMockPlayer(ctx);
							player.updatePosition(0,0,0);
							player.getInventory().setStack(5, new ItemStack(Items.DIAMOND));
							player.getEnderChestInventory().setStack(2, new ItemStack(Items.DIAMOND));

							var boat = EntityType.ACACIA_BOAT.spawn(
									ctx.getWorld(), BlockPos.ORIGIN, SpawnReason.LOAD
							);
							var pig = EntityType.PIG.spawn(ctx.getWorld(), BlockPos.ORIGIN, SpawnReason.LOAD);
							pig.startRiding(boat);
							var boat2 = EntityType.ACACIA_BOAT.spawn(
									ctx.getWorld(), BlockPos.ORIGIN, SpawnReason.LOAD
							);
							boat2.startRiding(boat);
							player.startRiding(boat2);

							var pearl = new ItemStack(Items.ENDER_PEARL);
							player.equipStack(EquipmentSlot.MAINHAND, pearl);
							pearl.use(player.getWorld(), player, Hand.MAIN_HAND);
							var pearlInWorld = player.getEnderPearls().stream().findAny().orElseThrow();

							final Identifier temp = Identifier.of("test", "test");
							PlayerDataHelper.saveCurrentPlayerData(player, temp);
							PlayerDataHelper.unloadPlayerConnectedEntities(player);
							PlayerDataHelper.resetPlayerData(player);

							ctx.assertTrue(pearlInWorld.isRemoved(), "Ender pearl remained!");
							ctx.assertTrue(boat.isRemoved(), "Boat remained!");
							ctx.assertTrue(pig.isRemoved(), "Pig remained!");
							ctx.assertTrue(player.getInventory().getStack(5).isEmpty(), "Diamond remained!");
							ctx.assertTrue(player.getEnderChestInventory().getStack(2).isEmpty(), "EnderChest Diamond remained!");

							ctx.runAtTick(1, () -> {
								PlayerDataHelper.loadPlayerData(player, temp, false, true);

								ctx.assertTrue(player.getEnderPearls().contains(getNewEntity(ctx, pearlInWorld)), "Ender pearl was not connected to player!");

								var midBoat = getNewEntity(ctx, boat2);
								var rootBoat = getNewEntity(ctx, boat);
								ctx.assertTrue(midBoat.getVehicle() == rootBoat, "Mid boat not riding root boat.");
								ctx.assertTrue(getNewEntity(ctx, pig).getVehicle() == rootBoat, "Pig not in boat!");
								ctx.assertTrue(player.getVehicle() == midBoat, "Pig not in boat!");

								ctx.assertTrue(player.getInventory().getStack(5).getItem() == Items.DIAMOND, "Diamond is lost!");
								ctx.assertTrue(player.getEnderChestInventory().getStack(2).getItem() == Items.DIAMOND, "EnderChest Diamond is lost!");

								ctx.complete();
							});
						}
				),
				new TestFunction(
						"player-data-test", "player-relog", "empty",
						1, 1, true,
						ctx -> {
							UUID id = UUID.randomUUID();
							String test = "test";
							var player = TestHelper.addMockPlayer(ctx, test, id);
							player.updatePosition(0,0,0);
							player.getInventory().setStack(5, new ItemStack(Items.DIAMOND));

							final Identifier temp = Identifier.of("test", "test");
							PlayerDataHelper.saveCurrentPlayerData(player, temp);
							PlayerDataHelper.unloadPlayerConnectedEntities(player);
							PlayerDataHelper.resetPlayerData(player);

							player.networkHandler.disconnect(Text.empty());

							ctx.runAtTick(1, () -> {
								var playerRelogged = TestHelper.addMockPlayer(ctx, test, id);
								PlayerDataHelper.loadPlayerData(playerRelogged, temp, false, true);

								ctx.assertTrue(playerRelogged.getInventory().getStack(5).getItem() == Items.DIAMOND, "Diamond is lost!");

								ctx.complete();
							});
						}
				),
				new TestFunction(
						"player-data-test", "save-and-load", "empty",
						1, 1, true, ctx -> {
							var player = TestHelper.addMockPlayer(ctx);
							player.updatePosition(10,0,0);

							var player2 = TestHelper.addMockPlayer(ctx);
							player2.updatePosition(12,0,0);

							var boat = EntityType.ACACIA_BOAT.spawn(
									ctx.getWorld(), new BlockPos(11, 0, 0), SpawnReason.LOAD
							);

							player.startRiding(boat);
							player2.startRiding(boat);

							PlayerDataHelper.unloadPlayerConnectedEntities(player);
							ctx.assertFalse(boat.isRemoved(), "Boat was removed despite another player riding it!");

							ctx.complete();
						}
				)
		));
	}

}
