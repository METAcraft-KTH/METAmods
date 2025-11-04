import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.PlayerDataHelper;
import nu.metacraft.lib.util.helper.TestHelper;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.UUID;

@ExtendWith(TestInit.class)
public class TestPlayerData {

	private static final String PLAYER_DATA_PREFIX = "player-data-test/";
	private static final ProblemReporter.PathElement TEST = () -> "test";

	public static void init() {
		Registry.register(
				BuiltInRegistries.TEST_FUNCTION,
				METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load"),
				ctx -> {
					var player = TestHelper.addMockPlayer(ctx);
					player.getInventory().setItem(5, new ItemStack(Items.DIAMOND));
					player.getEnderChestInventory().setItem(2, new ItemStack(Items.DIAMOND));

					var boat = EntityType.ACACIA_BOAT.spawn(
							ctx.getLevel(), player.blockPosition(), EntitySpawnReason.LOAD
					);
					var pig = EntityType.PIG.spawn(ctx.getLevel(), player.blockPosition(), EntitySpawnReason.LOAD);
					pig.startRiding(boat);
					var boat2 = EntityType.ACACIA_BOAT.spawn(
							ctx.getLevel(), player.blockPosition(), EntitySpawnReason.LOAD
					);
					boat2.startRiding(boat);
					player.startRiding(boat2);

					var pearl = new ItemStack(Items.ENDER_PEARL);
					player.setItemSlot(EquipmentSlot.MAINHAND, pearl);
					pearl.use(player.level(), player, InteractionHand.MAIN_HAND);
					var pearlInWorld = player.getEnderPearls().stream().findAny().orElseThrow();
					pearlInWorld.noPhysics = true;
					pearlInWorld.setNoGravity(true);
					pearlInWorld.setDeltaMovement(Vec3.ZERO);

					final ResourceLocation temp = ResourceLocation.fromNamespaceAndPath("test", "test");
					PlayerDataHelper.saveCurrentPlayerData(player, temp);
					PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
					PlayerDataHelper.resetPlayerData(player);

					ctx.assertFalse(player.isRemoved(), Component.literal("Player was removed!"));
					ctx.assertTrue(pearlInWorld.isRemoved(), Component.literal("Ender pearl remained!"));
					ctx.assertTrue(boat.isRemoved(), Component.literal("Boat remained!"));
					ctx.assertTrue(pig.isRemoved(), Component.literal("Pig remained!"));
					ctx.assertTrue(player.getInventory().getItem(5).isEmpty(), Component.literal("Diamond remained!"));
					ctx.assertTrue(player.getEnderChestInventory().getItem(2).isEmpty(), Component.literal("EnderChest Diamond remained!"));

					ctx.runAtTickTime(1, () -> {
						PlayerDataHelper.loadPlayerData(player, temp, false, true, true);
					});

					ctx.runAtTickTime(2, () -> {
						ctx.assertTrue(player.getEnderPearls().contains(getNewEntity(ctx, pearlInWorld)), Component.literal("Ender pearl was not connected to player!"));

						var midBoat = getNewEntity(ctx, boat2);
						var rootBoat = getNewEntity(ctx, boat);
						ctx.assertTrue(midBoat.getVehicle() == rootBoat, Component.literal("Mid boat not riding root boat."));
						ctx.assertTrue(getNewEntity(ctx, pig).getVehicle() == rootBoat, Component.literal("Pig not in boat!"));
						ctx.assertTrue(player.getVehicle() == midBoat, Component.literal("Pig not in boat!"));

						ctx.assertTrue(player.getInventory().getItem(5).getItem() == Items.DIAMOND, Component.literal("Diamond is lost!"));
						ctx.assertTrue(player.getEnderChestInventory().getItem(2).getItem() == Items.DIAMOND, Component.literal("EnderChest Diamond is lost!"));

						ctx.succeed();
					});
				}
		);
		Registry.register(
				BuiltInRegistries.TEST_FUNCTION,
				METAcraftLib.getID(PLAYER_DATA_PREFIX + "player-relog"),
				ctx -> {
					UUID id = UUID.randomUUID();
					String test = "test";
					var player = TestHelper.addMockPlayer(ctx, test, id);
					player.getInventory().setItem(5, new ItemStack(Items.DIAMOND));

					final ResourceLocation temp = ResourceLocation.fromNamespaceAndPath("test", "test");
					PlayerDataHelper.saveCurrentPlayerData(player, temp);
					PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
					PlayerDataHelper.resetPlayerData(player);

					player.connection.disconnect(Component.empty());

					ctx.runAtTickTime(1, () -> {
						var playerRelogged = TestHelper.addMockPlayer(ctx, test, id);
						PlayerDataHelper.loadPlayerData(playerRelogged, temp, false, true, true);

						ctx.assertTrue(playerRelogged.getInventory().getItem(5).getItem() == Items.DIAMOND, Component.literal("Diamond is lost!"));

						ctx.succeed();
					});
				}
		);
		Registry.register(
				BuiltInRegistries.TEST_FUNCTION,
				METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load-2"),
				ctx -> {
					var player = TestHelper.addMockPlayer(ctx);

					var player2 = TestHelper.addMockPlayer(ctx);

					var boat = EntityType.ACACIA_BOAT.spawn(
							ctx.getLevel(), player.blockPosition(), EntitySpawnReason.LOAD
					);

					player.startRiding(boat);
					player2.startRiding(boat);

					PlayerDataHelper.unloadPassengersAndVehicles(player);
					ctx.assertFalse(boat.isRemoved(), Component.literal("Boat was removed despite another player riding it!"));

					ctx.succeed();
				}
		);
		Registry.register(
				BuiltInRegistries.TEST_FUNCTION,
				METAcraftLib.getID(PLAYER_DATA_PREFIX + "save-and-load-3"),
				ctx -> {
					var player = TestHelper.addMockPlayer(ctx);

					try {
						var oldPlayerDataWithItem = TagParser.parseCompoundFully("{seenCredits: 0b, EnderItems: [], ShoulderEntityLeft: {}, ShoulderEntityRight: {}, Inventory: [{count: 1, Slot: 0b, components: {\"minecraft:food\": {saturation: 1.0f, nutrition: 1}}, id: \"minecraft:diamond\"}], DataVersion: 3955}");
						var oldPlayerData = TagParser.parseCompoundFully("{seenCredits: 0b, EnderItems: [], ShoulderEntityLeft: {}, ShoulderEntityRight: {}, Inventory: [], DataVersion: 3955}");
						var version = NbtUtils.getDataVersion(oldPlayerData, 1343);

						var id = ResourceLocation.fromNamespaceAndPath("test", "test");
						CompoundTag dataMap = new CompoundTag();
						dataMap.put(id.toString(), oldPlayerDataWithItem);
						oldPlayerData.put(PlayerDataHelper.PLAYER_DATA_ELEMENT, dataMap);
						var fixer = ctx.getLevel().getServer().getFixerUpper();

						oldPlayerData = (CompoundTag) fixer.update(
								References.PLAYER, new Dynamic<>(NbtOps.INSTANCE, oldPlayerData),
								version, SharedConstants.getCurrentVersion().dataVersion().version()
						).getValue();

						try (var logging = LoggingErrorReporter.create(TEST, METAcraftLib.LOGGER)) {
							var readView = TagValueInput.create(logging, player.registryAccess(), oldPlayerData);
							player.load(readView);
						}

						PlayerDataHelper.loadPlayerData(player, id, false, false, false);

						ctx.assertValueEqual(player.getMainHandItem().getItem(), Items.DIAMOND, Component.literal("Diamond went missing!"));
						ctx.assertTrue(player.getMainHandItem().has(DataComponents.CONSUMABLE), Component.literal("Item was not upgraded properly!"));

						ctx.succeed();
					} catch (CommandSyntaxException e) {
						throw new RuntimeException(e);
					}
				}
		);
	}

	private static Entity getNewEntity(GameTestHelper ctx, Entity oldEntity) {
		var entity = ctx.getLevel().getEntity(oldEntity.getUUID());
		ctx.assertTrue(entity != null, Component.empty().append(oldEntity.getDisplayName()).append(" went missing!"));
		return entity;
	}

	@Test
	public void testPlayerDataMap() throws IOException, ContentValidationException, InterruptedException {
		TestHelper.runTestServer(METAcraftLib.NAMESPACE,PLAYER_DATA_PREFIX+"*");
	}

}
