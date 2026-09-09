import com.google.common.base.Suppliers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class CutsceneTests {

	private static final IntervalMap<TransitionConfig> SIMPLE_CLEARING_CUTSCENE = new IntervalMap<>(List.of(
			new IntervalMap.Interval<>(
					0, 10, new SetGameModeTransition.Config(GameType.SPECTATOR, true)
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

	public static PlayerTeam getPlayerTeam(Scoreboard scoreboard) {
		final var name = "testing";
		var team = scoreboard.getPlayerTeam(name);
		if (team != null) {
			return team;
		}
		team = scoreboard.addPlayerTeam(name);
		team.setCollisionRule(Team.CollisionRule.NEVER);
		return team;
	}

	@BeforeAll
	public static void init() {
		TestHelper.init(
				() -> {
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTickTime(22, () -> {
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/erase"),
							context -> {
								var data = prepare(context, clearPlayer2.get());
								context.runAtTickTime(22, () -> {
									checkData(context, data, false, true, false);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/erase_but_return_to_start"),
							context -> {
								var data = prepare(context, clearPlayerButReturnToStart2.get());
								context.runAtTickTime(22, () -> {
									checkData(context, data, false, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_not_pos"),
							context -> {
								var data = prepare(context, preservePlayerButNotPos2.get());
								context.runAtTickTime(22, () -> {
									checkData(context, data, true, true, false);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_cutscene_stops"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTickTime(6, () -> {
									CutsceneHelper.stopPlayerSpecificCutscene(data.player);
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_cutscene_stops_2"),
							context -> {
								var data = prepare(context, preservePlayer2.get());
								context.runAtTickTime(15, () -> {
									CutsceneHelper.stopPlayerSpecificCutscene(data.player);
									checkData(context, data, true, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_leaves"),
							context -> {
								var data = prepare(context);
								var manager = MultiplayerCutsceneManager.getInstance(context.getLevel().getServer());
								String name = "test";
								manager.addCutscene(name, preservePlayer2.get(), context.getLevel());
								manager.addToCutscene(name, data.player);
								context.runAtTickTime(6, () -> {
									manager.leaveCutscene(data.player);
									checkData(context, data, true, true, true, false);
								});
								context.runAtTickTime(7, () -> {
									manager.endCutscene(name); //Cleanup
									context.succeed();
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_leaves_2"),
							context -> {
								var data = prepare(context);
								var manager = MultiplayerCutsceneManager.getInstance(context.getLevel().getServer());
								String name = "test2";
								manager.addCutscene(name, preservePlayer2.get(), context.getLevel());
								manager.addToCutscene(name, data.player);
								context.runAtTickTime(15, () -> {
									manager.leaveCutscene(data.player);
									checkData(context, data, true, true, true, false);
								});
								context.runAtTickTime(16, () -> {
									manager.endCutscene(name); //Cleanup
									context.succeed();
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_while_scene_ticks"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								String sceneName = "test3";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getLevel().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getLevel());
								manager.addToCutscene(sceneName, data.getValue().player);

								UUID uuid2 = UUID.randomUUID();
								MutableObject<ServerPlayer> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
								context.getLevel().getScoreboard().addPlayerToTeam(
										extraPlayer.getValue().getScoreboardName(),
										getPlayerTeam(context.getLevel().getScoreboard())
								);
								manager.addToCutscene(sceneName, extraPlayer.getValue());


								context.runAtTickTime(5, () -> {
									data.getValue().player.connection.disconnect(Component.empty());
								});
								context.runAtTickTime(6, () -> {
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
								});
								context.runAtTickTime(7, () -> {
									if (ItemStack.matches(data.getValue().stack, data.getValue().player.getInventory().getItem(data.getValue().slot))) {
										context.fail(Component.literal("Player was restored too early!"));
									}
								});

								context.runAtTickTime(8, () -> {
									data.getValue().player.connection.disconnect(Component.empty());
								});

								context.runAtTickTime(13, () -> {
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
								});
								context.runAtTickTime(14, () -> {
									if (ItemStack.matches(data.getValue().stack, data.getValue().player.getInventory().getItem(data.getValue().slot))) {
										context.fail(Component.literal("Player was restored too early!"));
									}
								});



								context.runAtTickTime(23, () -> {
									checkData(context, data.getValue(), true, true, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_after_end"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								UUID uuid2 = UUID.randomUUID();
								String sceneName = "test4";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getLevel().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getLevel());
								manager.addToCutscene(sceneName, data.getValue().player);

								MutableObject<ServerPlayer> extraPlayer = new MutableObject<>(TestHelper.addMockPlayer(context, name, uuid2));
								context.getLevel().getScoreboard().addPlayerToTeam(
										extraPlayer.getValue().getScoreboardName(),
										getPlayerTeam(context.getLevel().getScoreboard())
								);
								extraPlayer.getValue().getInventory().add(2, new ItemStack(Items.DIAMOND, 64));
								manager.addToCutscene(sceneName, extraPlayer.getValue());

								context.runAtTickTime(5, () -> {
									data.getValue().player.connection.disconnect(Component.empty());
									extraPlayer.getValue().connection.disconnect(Component.empty());
								});
								context.runAtTickTime(6, () -> {
									manager.endCutscene(sceneName);
									extraPlayer.setValue(TestHelper.addMockPlayer(context, name, uuid2));
								});
								context.runAtTickTime(7, () -> {
									if (extraPlayer.getValue().getInventory().getItem(2).getItem() != Items.DIAMOND) {
										context.fail(Component.literal("Other player was not reset properly!"));
									}
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
									checkData(context, data.getValue(), true, true, true);
								});
							}
					);
					Registry.register(
							BuiltInRegistries.TEST_FUNCTION, Cutscenes.getID("cutscene/preserve_but_player_disconnects_and_rejoins_while_paused"),
							context -> {
								String name = "mock-rejoiner";
								UUID uuid = UUID.randomUUID();
								String sceneName = "test5";
								MutableObject<Data> data = new MutableObject<>(prepare(context, TestHelper.addMockPlayer(context, name, uuid)));
								var manager = MultiplayerCutsceneManager.getInstance(context.getLevel().getServer());
								manager.addCutscene(sceneName, preservePlayer2.get(), context.getLevel());
								manager.addToCutscene(sceneName, data.getValue().player);


								context.runAtTickTime(5, () -> {
									data.getValue().player.connection.disconnect(Component.empty());
								});
								context.runAtTickTime(6, () -> {
									data.setValue(data.getValue().withPlayer(TestHelper.addMockPlayer(context, name, uuid)));
								});
								context.runAtTickTime(7, () -> {
									if (ItemStack.matches(data.getValue().stack, data.getValue().player.getInventory().getItem(data.getValue().slot))) {
										context.fail(Component.literal("Player was restored too early!"));
									}
								});



								context.runAtTickTime(23, () -> {
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

	private record Data(ServerPlayer player, ItemStack stack, int slot, UUID pig, UUID pearl, Vec3 startPos) {
		public Data withPlayer(ServerPlayer player) {
			return new Data(player, stack, slot, pig, pearl, startPos);
		}
	}

	private static Data prepare(
			GameTestHelper context
	) {
		return prepare(context, TestHelper.addMockPlayer(context));
	}

	private static Data prepare(
			GameTestHelper context, ServerPlayer player
	) {
		var team = getPlayerTeam(player.level().getScoreboard());
		player.level().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
		var stack = new ItemStack(Items.DIAMOND, 64);
		int slot = 5;
		var start = player.position();
		player.getInventory().add(slot, stack.copy());
		var pig = EntityTypes.PIG.create(context.getLevel(), EntitySpawnReason.TRIGGERED);
		pig.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		pig.absSnapTo(start.x(), start.y(), start.z());
		var pearl = new ThrownEnderpearl(player.level(), player, new ItemStack(Items.ENDER_PEARL));
		pearl.absSnapTo(start.x(), start.y(), start.z());
		pearl.setNoGravity(true);
		pearl.noPhysics = true;
		context.getLevel().addFreshEntity(pearl);
		context.getLevel().addFreshEntity(pig);
		player.startRiding(pig);
		var pos = player.position();
		return new Data(player, stack, slot, pig.getUUID(), pearl.getUUID(), pos);
	}

	private static Data prepare(
			GameTestHelper context, Cutscene cutscene
	) {
		var data = prepare(context);
		CutsceneHelper.playPlayerSpecificCutscene(data.player, cutscene);
		context.assertTrue(context.getLevel().getEntity(data.pearl) == null, Component.literal("Ender Pearl was not removed!"));
		context.assertTrue(context.getLevel().getEntity(data.pig) == null, Component.literal("Pig was not removed!"));
		return data;
	}
	private static void checkData(
			GameTestHelper ctx, Data data, boolean shouldKeepStack,
			boolean shouldPigStayMounted, boolean shouldMaintainPos
	) {
		checkData(ctx, data, shouldKeepStack, shouldPigStayMounted, shouldMaintainPos, true);
	}

	private static void checkData(
			GameTestHelper ctx, Data data, boolean shouldKeepStack,
			boolean shouldPigStayMounted, boolean shouldMaintainPos,
			boolean isDone
	) {
		boolean itemIsStillHere = ItemStack.matches(data.stack, data.player.getInventory().getItem(data.slot));
		var pig = ctx.getLevel().getEntity(data.pig);
		if (pig == null) {
			ctx.fail(Component.literal("Pig went missing!"));
		}
		boolean pigRemounted = pig.hasPassenger(data.player);
		if (ctx.getLevel().getEntity(data.pearl) == null) {
			ctx.fail(Component.literal("Ender Pearl went missing!"));
		}
		if (itemIsStillHere && !shouldKeepStack) {
			ctx.fail(Component.literal("Item was kept, but was supposed to disappear!"));
		}
		if (!itemIsStillHere && shouldKeepStack) {
			ctx.fail(Component.literal("Item was erased, but was supposed to be kept!"));
		}

		if (pigRemounted && !shouldPigStayMounted) {
			ctx.fail(Component.literal("Pig was still mounted, but was supposed to be dismounted!"));
		}
		if (!pigRemounted && shouldPigStayMounted) {
			ctx.fail(Component.literal("Pig not mounted, but was supposed to be mounted!"));
		}

		boolean posMaintained = data.player.position().distanceTo(data.startPos) < 2;

		if (posMaintained && !shouldMaintainPos) {
			ctx.fail(Component.literal("Player should not have been returned to start, but they were!"));
		}
		if (!posMaintained && shouldMaintainPos) {
			ctx.fail(Component.literal("Player was not returned to the right position when they should have!"));
		}

		if (isDone) {
			ctx.succeed();
		}
	}

	@Test
	public void checkPlayerData() throws Exception {
		TestHelper.runTestServer(
				"metacraft", "cutscene/*"
		);
	}


}
