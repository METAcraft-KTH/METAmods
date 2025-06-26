import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.storage.NbtReadView;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.path.SymlinkValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.error_reporters.LoggingErrorReporter;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.TestHelper;

import java.io.IOException;
import java.util.UUID;

public class TestPlayerData {

	private static final String PLAYER_DATA_PREFIX = "player-data-test/";
	private static final ErrorReporter.Context TEST = () -> "test";

	@BeforeAll
	public static void init() {
		TestHelper.init(
				() -> {
					Registry.register(
							Registries.TEST_FUNCTION,
							METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load"),
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
								PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
								PlayerDataHelper.resetPlayerData(player);

								ctx.assertFalse(player.isRemoved(), Text.literal("Player was removed!"));
								ctx.assertTrue(pearlInWorld.isRemoved(), Text.literal("Ender pearl remained!"));
								ctx.assertTrue(boat.isRemoved(), Text.literal("Boat remained!"));
								ctx.assertTrue(pig.isRemoved(), Text.literal("Pig remained!"));
								ctx.assertTrue(player.getInventory().getStack(5).isEmpty(), Text.literal("Diamond remained!"));
								ctx.assertTrue(player.getEnderChestInventory().getStack(2).isEmpty(), Text.literal("EnderChest Diamond remained!"));

								ctx.runAtTick(1, () -> {
									PlayerDataHelper.loadPlayerData(player, temp, false, true, true);
								});

								ctx.runAtTick(2, () -> {
									ctx.assertTrue(player.getEnderPearls().contains(getNewEntity(ctx, pearlInWorld)), Text.literal("Ender pearl was not connected to player!"));

									var midBoat = getNewEntity(ctx, boat2);
									var rootBoat = getNewEntity(ctx, boat);
									ctx.assertTrue(midBoat.getVehicle() == rootBoat, Text.literal("Mid boat not riding root boat."));
									ctx.assertTrue(getNewEntity(ctx, pig).getVehicle() == rootBoat, Text.literal("Pig not in boat!"));
									ctx.assertTrue(player.getVehicle() == midBoat, Text.literal("Pig not in boat!"));

									ctx.assertTrue(player.getInventory().getStack(5).getItem() == Items.DIAMOND, Text.literal("Diamond is lost!"));
									ctx.assertTrue(player.getEnderChestInventory().getStack(2).getItem() == Items.DIAMOND, Text.literal("EnderChest Diamond is lost!"));

									ctx.complete();
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION,
							METAcraftLib.getID(PLAYER_DATA_PREFIX + "player-relog"),
							ctx -> {
								UUID id = UUID.randomUUID();
								String test = "test";
								var player = TestHelper.addMockPlayer(ctx, test, id);
								player.getInventory().setStack(5, new ItemStack(Items.DIAMOND));

								final Identifier temp = Identifier.of("test", "test");
								PlayerDataHelper.saveCurrentPlayerData(player, temp);
								PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
								PlayerDataHelper.resetPlayerData(player);

								player.networkHandler.disconnect(Text.empty());

								ctx.runAtTick(1, () -> {
									var playerRelogged = TestHelper.addMockPlayer(ctx, test, id);
									PlayerDataHelper.loadPlayerData(playerRelogged, temp, false, true, true);

									ctx.assertTrue(playerRelogged.getInventory().getStack(5).getItem() == Items.DIAMOND, Text.literal("Diamond is lost!"));

									ctx.complete();
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION,
							METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load-2"),
							ctx -> {
								var player = TestHelper.addMockPlayer(ctx);

								var player2 = TestHelper.addMockPlayer(ctx);

								var boat = EntityType.ACACIA_BOAT.spawn(
										ctx.getWorld(), new BlockPos(11, 0, 0), SpawnReason.LOAD
								);

								player.startRiding(boat);
								player2.startRiding(boat);

								PlayerDataHelper.unloadPassengersAndVehicles(player);
								ctx.assertFalse(boat.isRemoved(), Text.literal("Boat was removed despite another player riding it!"));

								ctx.complete();
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION,
							METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load-3"),
							ctx -> {
								var player = TestHelper.addMockPlayer(ctx);

								try {
									var oldPlayerDataWithItem = StringNbtReader.readCompound("{seenCredits: 0b, EnderItems: {}, ShoulderEntityLeft: {}, ShoulderEntityRight: {}, Inventory: [{count: 1, Slot: 0b, components: {\"minecraft:food\": {saturation: 1.0f, nutrition: 1}}, id: \"minecraft:diamond\"}], DataVersion: 3955}");
									var oldPlayerData = StringNbtReader.readCompound("{seenCredits: 0b, EnderItems: {}, ShoulderEntityLeft: {}, ShoulderEntityRight: {}, DataVersion: 3955}");
									var version = NbtHelper.getDataVersion(oldPlayerData, 1343);

									var id = Identifier.of("test", "test");
									NbtCompound dataMap = new NbtCompound();
									dataMap.put(id.toString(), oldPlayerDataWithItem);
									oldPlayerData.put(PlayerDataHelper.PLAYER_DATA_ELEMENT, dataMap);
									var fixer = ctx.getWorld().getServer().getDataFixer();

									oldPlayerData = (NbtCompound) fixer.update(
											TypeReferences.PLAYER, new Dynamic<>(NbtOps.INSTANCE, oldPlayerData),
											version, SharedConstants.getGameVersion().dataVersion().id()
									).getValue();

									try (var logging = LoggingErrorReporter.create(TEST, METAcraftLib.LOGGER)) {
										var readView = NbtReadView.create(logging, player.getRegistryManager(), oldPlayerData);
										player.readData(readView);
									}

									PlayerDataHelper.loadPlayerData(player, id, false, false, false);

									ctx.assertEquals(player.getMainHandStack().getItem(), Items.DIAMOND, Text.literal("Diamond went missing!"));
									ctx.assertTrue(player.getMainHandStack().contains(DataComponentTypes.CONSUMABLE), Text.literal("Item was not upgraded properly!"));

									ctx.complete();
								} catch (CommandSyntaxException e) {
									throw new RuntimeException(e);
								}
							}
					);
				},
				METAcraftLib::new
		);
	}

	private static Entity getNewEntity(TestContext ctx, Entity oldEntity) {
		var entity = ctx.getWorld().getEntity(oldEntity.getUuid());
		ctx.assertTrue(entity != null, Text.empty().append(oldEntity.getDisplayName()).append(" went missing!"));
		return entity;
	}

	@Test
	public void testPlayerDataMap() throws IOException, SymlinkValidationException, InterruptedException {
		TestHelper.runTestServer(METAcraftLib.NAMESPACE,PLAYER_DATA_PREFIX+"*");
	}

}
