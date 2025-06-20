import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestFunction;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;
import se.datasektionen.mc.cutscenes.transitions.RunCommandTransition;
import se.datasektionen.mc.cutscenes.transitions.SetGameModeTransition;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.helper.TestHelper;
import se.datasektionen.mc.resource_packs.ResourcePacks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CutsceneTests {

	@BeforeAll
	public static void init() {
		TestHelper.init(
				METAcraftLib::new,
				METAcraftCore::new,
				ResourcePacks::new,
				Cutscenes::new
		);
	}

	private static final IntervalMap<TransitionConfig> SIMPLE_CLEARING_CUTSCENE = new IntervalMap<>(List.of(
			new IntervalMap.Interval<>(
					0, 10, new SetGameModeTransition.Config(GameMode.SPECTATOR, true)
			),
			new IntervalMap.Interval<>(
					0, 10,
					new RunCommandTransition(
							Optional.of("clear @s"),
							Optional.empty(),
							Optional.empty(),
							false, true, false
					)
			),
			new IntervalMap.Interval<>(
					0, 10,
					new RunCommandTransition(
							Optional.of("execute at @s run tp @s ~8 ~ ~"),
							Optional.empty(),
							Optional.empty(),
							false, true, false
					)
			)
	));

	private record Data(ServerPlayerEntity player, ItemStack stack, int slot, UUID pig, UUID pearl, Vec3d startPos) {
		public Data withPlayer(ServerPlayerEntity player) {
			return new Data(player, stack, slot, pig, pearl, startPos);
		}
	}

	private static Data prepare(
			TestContext context
	) {
		return prepare(context, TestHelper.addMockPlayer(context));
	}

	private static Data prepare(
			TestContext context, ServerPlayerEntity player
	) {
		var stack = new ItemStack(Items.DIAMOND, 64);
		int slot = 5;
		player.getInventory().insertStack(slot, stack.copy());
		var pig = EntityType.PIG.create(context.getWorld(), SpawnReason.TRIGGERED);
		pig.saddle(null, null);
		var pearl = new EnderPearlEntity(player.getWorld(), player, new ItemStack(Items.ENDER_PEARL));
		context.getWorld().spawnEntity(pearl);
		context.getWorld().spawnEntity(pig);
		player.startRiding(pig);
		var pos = player.getPos();
		return new Data(player, stack, slot, pig.getUuid(), pearl.getUuid(), pos);
	}

	private static Data prepare(
			TestContext context, Cutscene cutscene
	) {
		var data = prepare(context);
		CutsceneHelper.playPlayerSpecificCutscene(data.player, cutscene);
		context.assertTrue(context.getWorld().getEntity(data.pearl) == null, "Ender Pearl was not removed!");
		context.assertTrue(context.getWorld().getEntity(data.pig) == null, "Pig was not removed!");
		return data;
	}
	private static void checkData(
			TestContext ctx, Data data, boolean shouldKeepStack,
			boolean shouldPigStayMounted, boolean shouldMaintainPos
	) {
		checkData(ctx, data, shouldKeepStack, shouldPigStayMounted, shouldMaintainPos, true);
	}

	private static void checkData(
			TestContext ctx, Data data, boolean shouldKeepStack,
			boolean shouldPigStayMounted, boolean shouldMaintainPos,
			boolean isDone
	) {
		boolean itemIsStillHere = ItemStack.areEqual(data.stack, data.player.getInventory().getStack(data.slot));
		var pig = ctx.getWorld().getEntity(data.pig);
		if (pig == null) {
			ctx.throwGameTestException("Pig went missing!");
		}
		boolean pigRemounted = pig.hasPassenger(data.player);
		if (ctx.getWorld().getEntity(data.pearl) == null) {
			ctx.throwGameTestException("Ender Pearl went missing!");
		}
		if (itemIsStillHere && !shouldKeepStack) {
			ctx.throwGameTestException("Item was kept, but was supposed to disappear!");
		}
		if (!itemIsStillHere && shouldKeepStack) {
			ctx.throwGameTestException("Item was erased, but was supposed to be kept!");
		}

		if (pigRemounted && !shouldPigStayMounted) {
			ctx.throwGameTestException("Pig was still mounted, but was supposed to be dismounted!");
		}
		if (!pigRemounted && shouldPigStayMounted) {
			ctx.throwGameTestException("Pig not mounted, but was supposed to be mounted!");
		}

		boolean posMaintained = data.player.getPos().equals(data.startPos);

		if (posMaintained && !shouldMaintainPos) {
			ctx.throwGameTestException("Player should not have been returned to start, but they were!");
		}
		if (!posMaintained && shouldMaintainPos) {
			ctx.throwGameTestException("Player was not returned to the right position when they should have!");
		}

		if (isDone) {
			ctx.complete();
		}
	}

	@Test
	public void checkPlayerData() throws Exception {
		var clearPlayer = new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(false).resetPlayerData(false);
		var clearPlayer2 = new Cutscene(SIMPLE_CLEARING_CUTSCENE)
				.setReturnPlayerToStartPos(false).resetPlayerData(false).setCachedNextCutscene(clearPlayer);
		var clearPlayerButReturnToStart = new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(true).resetPlayerData(false);
		var preservePlayerButNotPos = new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(false).resetPlayerData(true);
		var preservePlayer = new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(true).resetPlayerData(true);
		var preservePlayer2 = new Cutscene(SIMPLE_CLEARING_CUTSCENE)
				.setReturnPlayerToStartPos(true).resetPlayerData(true).setCachedNextCutscene(preservePlayer);

		var clearPlayerButReturnToStart2 = new Cutscene(SIMPLE_CLEARING_CUTSCENE)
				.setReturnPlayerToStartPos(true).resetPlayerData(false).setCachedNextCutscene(clearPlayerButReturnToStart);
		var preservePlayerButNotPos2 = new Cutscene(SIMPLE_CLEARING_CUTSCENE)
				.setReturnPlayerToStartPos(false).resetPlayerData(true).setCachedNextCutscene(preservePlayerButNotPos);
		TestHelper.runTestServer(
				List.of(
						new TestFunction(
								"cutscenes", "preserve",
								"empty", 22, 22,
								true, context -> {
							var data = prepare(context, preservePlayer2);
							context.runAtTick(22, () -> {
								checkData(context, data, true, true, true);
							});
						}),
						new TestFunction(
								"cutscenes", "erase",
								"empty", 22, 22,
								true, context -> {
							var data = prepare(context, clearPlayer2);
							context.runAtTick(22, () -> {
								checkData(context, data, false, true, false);
							});
						}),
						new TestFunction(
								"cutscenes", "erase_but_return_to_start",
								"empty", 22, 22,
								true, context -> {
							var data = prepare(context, clearPlayerButReturnToStart2);
							context.runAtTick(22, () -> {
								checkData(context, data, false, true, true);
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_not_pos",
								"empty", 22, 22,
								true, context -> {
							var data = prepare(context, preservePlayerButNotPos2);
							context.runAtTick(22, () -> {
								checkData(context, data, true, true, false);
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_cutscene_stops",
								"empty", 5, 5,
								true, context -> {
							var data = prepare(context, preservePlayer2);
							context.runAtTick(5, () -> {
								CutsceneHelper.stopPlayerSpecificCutscene(data.player);
								checkData(context, data, true, true, true);
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_cutscene_stops_2",
								"empty", 15, 15,
								true, context -> {
							var data = prepare(context, preservePlayer2);
							context.runAtTick(15, () -> {
								CutsceneHelper.stopPlayerSpecificCutscene(data.player);
								checkData(context, data, true, true, true);
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_player_leaves",
								"empty", 6, 6,
								true, context -> {
							var data = prepare(context);
							var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
							String name = "test";
							manager.addCutscene(name, preservePlayer2, context.getWorld());
							manager.addToCutscene(name, data.player);
							context.runAtTick(5, () -> {
								manager.leaveCutscene(data.player);
								checkData(context, data, true, true, true, false);
							});
							context.runAtTick(6, () -> {
								manager.endCutscene(name); //Cleanup
								context.complete();
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_player_leaves_2",
								"empty", 16, 16,
								true, context -> {
							var data = prepare(context);
							var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
							String name = "test2";
							manager.addCutscene(name, preservePlayer2, context.getWorld());
							manager.addToCutscene(name, data.player);
							context.runAtTick(15, () -> {
								manager.leaveCutscene(data.player);
								checkData(context, data, true, true, true, false);
							});
							context.runAtTick(16, () -> {
								manager.endCutscene(name); //Cleanup
								context.complete();
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_player_disconnects_and_rejoins_while_scene_ticks",
								"empty", 23, 23,
								true, context -> {
							String name = "mock-rejoiner";
							UUID uuid = UUID.randomUUID();
							String sceneName = "test3";
							MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
							var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
							manager.addCutscene(sceneName, preservePlayer2, context.getWorld());
							manager.addToCutscene(sceneName, data.getValue().player);

							UUID uuid2 = UUID.randomUUID();
							MutableObject<ServerPlayerEntity> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
							manager.addToCutscene(sceneName, extraPlayer.getValue());


							context.runAtTick(5, () -> {
								data.getValue().player.networkHandler.disconnect(Text.empty());
							});
							context.runAtTick(6, () -> {
								data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
							});
							context.runAtTick(7, () -> {
								if (ItemStack.areEqual(data.getValue().stack, data.getValue().player.getInventory().getStack(data.getValue().slot))) {
									context.throwGameTestException("Player was restored too early!");
								}
							});

							context.runAtTick(8, () -> {
								data.getValue().player.networkHandler.disconnect(Text.empty());
							});

							context.runAtTick(13, () -> {
								data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
							});
							context.runAtTick(14, () -> {
								if (ItemStack.areEqual(data.getValue().stack, data.getValue().player.getInventory().getStack(data.getValue().slot))) {
									context.throwGameTestException("Player was restored too early!");
								}
							});



							context.runAtTick(22, () -> {
								checkData(context, data.getValue(), true, true, true, false);
							});

							context.runAtTick(23, context::complete); //Cleanup
						}),
						new TestFunction(
								"cutscenes", "preserve_but_player_disconnects_and_rejoins_after_end",
								"empty", 8, 8,
								true, context -> {
							String name = "mock-rejoiner";
							UUID uuid = UUID.randomUUID();
							UUID uuid2 = UUID.randomUUID();
							String sceneName = "test4";
							MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
							var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
							manager.addCutscene(sceneName, preservePlayer2, context.getWorld());
							manager.addToCutscene(sceneName, data.getValue().player);

							MutableObject<ServerPlayerEntity> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
							extraPlayer.getValue().getInventory().insertStack(2, new ItemStack(Items.DIAMOND, 64));
							manager.addToCutscene(sceneName, extraPlayer.getValue());

							context.runAtTick(5, () -> {
								data.getValue().player.networkHandler.disconnect(Text.empty());
								extraPlayer.getValue().networkHandler.disconnect(Text.empty());
							});
							context.runAtTick(6, () -> {
								manager.endCutscene(sceneName);
								extraPlayer.setValue(TestHelper.addMockPlayer(context, name, uuid2));
							});
							context.runAtTick(7, () -> {
								if (extraPlayer.getValue().getInventory().getStack(2).getItem() != Items.DIAMOND) {
									context.throwGameTestException("Other player was not reset properly!");
								}
								data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
							});

							context.runAtTick(8, () -> {
								checkData(context, data.getValue(), true, true, true);
							});
						}),
						new TestFunction(
								"cutscenes", "preserve_but_player_disconnects_and_rejoins_while_paused",
								"empty", 23, 23,
								true, context -> {
							String name = "mock-rejoiner";
							UUID uuid = UUID.randomUUID();
							String sceneName = "test5";
							MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
							var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
							manager.addCutscene(sceneName, preservePlayer2, context.getWorld());
							manager.addToCutscene(sceneName, data.getValue().player);


							context.runAtTick(5, () -> {
								data.getValue().player.networkHandler.disconnect(Text.empty());
							});
							context.runAtTick(6, () -> {
								data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
							});
							context.runAtTick(7, () -> {
								if (ItemStack.areEqual(data.getValue().stack, data.getValue().player.getInventory().getStack(data.getValue().slot))) {
									context.throwGameTestException("Player was restored too early!");
								}
							});



							context.runAtTick(23, () -> {
								checkData(context, data.getValue(), true, true, true);
							});
						})
				)
		);
	}


}
