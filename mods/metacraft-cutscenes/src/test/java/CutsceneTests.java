import com.google.common.base.Suppliers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import nu.metacraft.cutscenes.transitions.RunCommandTransition;
import nu.metacraft.cutscenes.transitions.SetGameModeTransition;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.resource_packs.ResourcePacks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class CutsceneTests {

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

	static final Supplier<Cutscene> clearPlayer = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(false).resetPlayerData(false)
	);
	static final Supplier<Cutscene> clearPlayer2 = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE)
					.setReturnPlayerToStartPos(false).resetPlayerData(false).setCachedNextCutscene(clearPlayer.get())
	);
	static final Supplier<Cutscene> clearPlayerButReturnToStart = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(true).resetPlayerData(false)
	);
	static final Supplier<Cutscene> preservePlayerButNotPos = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(false).resetPlayerData(true)
	);
	static final Supplier<Cutscene> preservePlayer = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE).setReturnPlayerToStartPos(true).resetPlayerData(true)
	);
	static final Supplier<Cutscene> preservePlayer2 = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE)
					.setReturnPlayerToStartPos(true).resetPlayerData(true).setCachedNextCutscene(preservePlayer.get())
	);

	static final Supplier<Cutscene> clearPlayerButReturnToStart2 = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE)
					.setReturnPlayerToStartPos(true).resetPlayerData(false).setCachedNextCutscene(clearPlayerButReturnToStart.get())
	);
	static final Supplier<Cutscene> preservePlayerButNotPos2 = Suppliers.memoize(
			() -> new Cutscene(SIMPLE_CLEARING_CUTSCENE)
					.setReturnPlayerToStartPos(false).resetPlayerData(true).setCachedNextCutscene(preservePlayerButNotPos.get())
	);

	public static Team getPlayerTeam(Scoreboard scoreboard) {
		final var name = "testing";
		var team = scoreboard.getTeam(name);
		if (team != null) {
			return team;
		}
		team = scoreboard.addTeam(name);
		team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
		return team;
	}

	@BeforeAll
	public static void init() {
		TestHelper.init(
				() -> {
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTick(22, () -> {
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/erase"),
							context -> {
								var data = prepare(context, clearPlayer2.get());
								context.runAtTick(22, () -> {
									checkData(context, data, false, true, false);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/erase_but_return_to_start"),
							context -> {
								var data = prepare(context, clearPlayerButReturnToStart2.get());
								context.runAtTick(22, () -> {
									checkData(context, data, false, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_not_pos"),
							context -> {
								var data = prepare(context, preservePlayerButNotPos2.get());
								context.runAtTick(22, () -> {
									checkData(context, data, true, true, false);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_cutscene_stops"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTick(6, () -> {
									CutsceneHelper.stopPlayerSpecificCutscene(data.player);
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_cutscene_stops_2"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTick(15, () -> {
									CutsceneHelper.stopPlayerSpecificCutscene(data.player);
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_leaves"),
							context -> {
								var data = prepare(context);
								var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
								String name = "test";
								manager.addCutscene(name, preservePlayer2.get(), context.getWorld());
								manager.addToCutscene(name, data.player);
								context.runAtTick(6, () -> {
									manager.leaveCutscene(data.player);
									checkData(context, data, true, true, true, false);
								});
								context.runAtTick(7, () -> {
									manager.endCutscene(name); //Cleanup
									context.complete();
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_leaves_2"),
							context -> {
								var data = prepare(context);
								var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
								String name = "test2";
								manager.addCutscene(name, preservePlayer2.get(), context.getWorld());
								manager.addToCutscene(name, data.player);
								context.runAtTick(15, () -> {
									manager.leaveCutscene(data.player);
									checkData(context, data, true, true, true, false);
								});
								context.runAtTick(16, () -> {
									manager.endCutscene(name); //Cleanup
									context.complete();
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_while_scene_ticks"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								String sceneName = "test3";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getWorld());
								manager.addToCutscene(sceneName, data.getValue().player);

								UUID uuid2 = UUID.randomUUID();
								MutableObject<ServerPlayerEntity> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
								context.getWorld().getScoreboard().addScoreHolderToTeam(
										extraPlayer.getValue().getNameForScoreboard(),
										getPlayerTeam(context.getWorld().getScoreboard())
								);
								manager.addToCutscene(sceneName, extraPlayer.getValue());


								context.runAtTick(5, () -> {
									data.getValue().player.networkHandler.disconnect(Text.empty());
								});
								context.runAtTick(6, () -> {
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
								});
								context.runAtTick(7, () -> {
									if (ItemStack.areEqual(data.getValue().stack, data.getValue().player.getInventory().getStack(data.getValue().slot))) {
										context.throwGameTestException(Text.literal("Player was restored too early!"));
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
										context.throwGameTestException(Text.literal("Player was restored too early!"));
									}
								});



								context.runAtTick(23, () -> {
									checkData(context, data.getValue(), true, true, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_after_end"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								UUID uuid2 = UUID.randomUUID();
								String sceneName = "test4";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getWorld());
								manager.addToCutscene(sceneName, data.getValue().player);

								MutableObject<ServerPlayerEntity> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
								context.getWorld().getScoreboard().addScoreHolderToTeam(
										extraPlayer.getValue().getNameForScoreboard(),
										getPlayerTeam(context.getWorld().getScoreboard())
								);
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
										context.throwGameTestException(Text.literal("Other player was not reset properly!"));
									}
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
									checkData(context, data.getValue(), true, true, true);
								});
							}
					);
					Registry.register(
							Registries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_while_paused"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								String sceneName = "test5";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getWorld().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getWorld());
								manager.addToCutscene(sceneName, data.getValue().player);


								context.runAtTick(5, () -> {
									data.getValue().player.networkHandler.disconnect(Text.empty());
								});
								context.runAtTick(6, () -> {
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
								});
								context.runAtTick(7, () -> {
									if (ItemStack.areEqual(data.getValue().stack, data.getValue().player.getInventory().getStack(data.getValue().slot))) {
										context.throwGameTestException(Text.literal("Player was restored too early!"));
									}
								});



								context.runAtTick(23, () -> {
									checkData(context, data.getValue(), true, true, true);
								});
							}
					);
				},
				METAcraftLib::new,
				METAcraftCore::new,
				ResourcePacks::new,
				Cutscenes::new
		);
	}

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
		var team = getPlayerTeam(player.getEntityWorld().getScoreboard());
		player.getEntityWorld().getScoreboard().addScoreHolderToTeam(player.getNameForScoreboard(), team);
		var stack = new ItemStack(Items.DIAMOND, 64);
		int slot = 5;
		var start = player.getPos();
		player.getInventory().insertStack(slot, stack.copy());
		var pig = EntityType.PIG.create(context.getWorld(), SpawnReason.TRIGGERED);
		pig.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		pig.updatePosition(start.getX(), start.getY(), start.getZ());
		var pearl = new EnderPearlEntity(player.getEntityWorld(), player, new ItemStack(Items.ENDER_PEARL));
		pearl.updatePosition(start.getX(), start.getY(), start.getZ());
		pearl.setNoGravity(true);
		pearl.noClip = true;
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
		context.assertTrue(context.getWorld().getEntity(data.pearl) == null, Text.literal("Ender Pearl was not removed!"));
		context.assertTrue(context.getWorld().getEntity(data.pig) == null, Text.literal("Pig was not removed!"));
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
			ctx.throwGameTestException(Text.literal("Pig went missing!"));
		}
		boolean pigRemounted = pig.hasPassenger(data.player);
		if (ctx.getWorld().getEntity(data.pearl) == null) {
			ctx.throwGameTestException(Text.literal("Ender Pearl went missing!"));
		}
		if (itemIsStillHere && !shouldKeepStack) {
			ctx.throwGameTestException(Text.literal("Item was kept, but was supposed to disappear!"));
		}
		if (!itemIsStillHere && shouldKeepStack) {
			ctx.throwGameTestException(Text.literal("Item was erased, but was supposed to be kept!"));
		}

		if (pigRemounted && !shouldPigStayMounted) {
			ctx.throwGameTestException(Text.literal("Pig was still mounted, but was supposed to be dismounted!"));
		}
		if (!pigRemounted && shouldPigStayMounted) {
			ctx.throwGameTestException(Text.literal("Pig not mounted, but was supposed to be mounted!"));
		}

		boolean posMaintained = data.player.getPos().distanceTo(data.startPos) < 2;

		if (posMaintained && !shouldMaintainPos) {
			ctx.throwGameTestException(Text.literal("Player should not have been returned to start, but they were!"));
		}
		if (!posMaintained && shouldMaintainPos) {
			ctx.throwGameTestException(Text.literal("Player was not returned to the right position when they should have!"));
		}

		if (isDone) {
			ctx.complete();
		}
	}

	@Test
	public void checkPlayerData() throws Exception {
		TestHelper.runTestServer(
				"metacraft", "cutscene/*"
		);
	}


}
