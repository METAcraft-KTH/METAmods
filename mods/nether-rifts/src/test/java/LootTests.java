import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import se.metacraft.nether_rifts.mob.RiftEntities;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.raid.RiftMobs;
import se.metacraft.nether_rifts.raid.Slot;

import java.util.Optional;

public class LootTests {

	static void registerTests() {
		NetherRiftsTests.register("loot/player_kills_drop", ctx -> killMany(ctx, Slot.RANGED, true, () -> {
			ctx.assertTrue(count(ctx, Items.QUARTZ) > 0, "ten troopers killed by a player dropped no quartz");
			ctx.assertTrue(count(ctx, Items.CROSSBOW) == 0, "a trooper dropped its crossbow");
		}));
		NetherRiftsTests.register("loot/other_kills_drop_nothing", ctx -> killMany(ctx, Slot.RANGED, false, () ->
				ctx.assertTrue(ctx.getEntities(EntityTypes.ITEM).isEmpty(), "troopers not killed by a player dropped items")));
		NetherRiftsTests.register("loot/elite_blaze_rod", ctx -> killMany(ctx, Slot.ELITE, true, () ->
				ctx.assertTrue(count(ctx, Items.BLAZE_ROD) >= 10, "brutes dropped " + count(ctx, Items.BLAZE_ROD) + " blaze rods, expected one each")));
		NetherRiftsTests.register("loot/captain_drops", ctx -> {
			// the General killed by a player: his blaze rod and quartz, and no head (the stand-in brute's was a Piglin General head)
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = StageTests.player(ctx);
				var general = (LivingEntity) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.CAPTAIN,
						ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				ctx.assertTrue(general instanceof PiglinGeneral, "the Captain's slot spawned " + general);
				general.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
				ctx.runAfterDelay(5, () -> {
					ctx.assertTrue(count(ctx, Items.PIGLIN_HEAD) == 0, "the General dropped a head");
					ctx.assertTrue(count(ctx, Items.BLAZE_ROD) == 1, "the General dropped " + count(ctx, Items.BLAZE_ROD) + " blaze rods");
					int quartz = count(ctx, Items.QUARTZ);
					ctx.assertTrue(quartz >= 4 && quartz <= 8, "the General dropped " + quartz + " quartz");
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("loot/war_ghast_drops_ghast_loot", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				ctx.assertTrue(ghast.getLootTable().equals(EntityTypes.GHAST.getDefaultLootTable()), "a war ghast carries the loot table " + ghast.getLootTable());
				ctx.killAllEntities();
				// ten war ghasts killed by a player: the vanilla ghast's tears and gunpowder (a ghast drops neither one time in six)
				killTen(ctx, Slot.BOMBARDIER, true, () -> ctx.assertTrue(count(ctx, Items.GHAST_TEAR) + count(ctx, Items.GUNPOWDER) > 0,
						"ten war ghasts dropped no ghast tear or gunpowder"));
			});
		});
		NetherRiftsTests.register("loot/summoned_war_ghast_drops_ghast_loot", ctx -> {
			// A /summon or spawn-egg war ghast has no rift, so it keeps its own table, which hands out a ghast's.
			var key = ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/war_ghast"));
			ctx.assertTrue(ctx.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "no entities/war_ghast");
			var player = StageTests.player(ctx);
			for (int i = 0; i < 10; i++) {
				var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 1, 12));
				ctx.assertTrue(ghast.getLootTable().equals(Optional.of(key)), "a summoned war ghast carries the loot table " + ghast.getLootTable());
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
			}
			ctx.runAfterDelay(5, () -> {
				ctx.assertTrue(count(ctx, Items.GHAST_TEAR) + count(ctx, Items.GUNPOWDER) > 0, "ten summoned war ghasts dropped no ghast tear or gunpowder");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("loot/victory_tables_exist", ctx -> {
			for (var tier : new String[] {"small", "medium", "large"}) {
				var key = ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("victory/" + tier));
				ctx.assertTrue(ctx.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "no victory/" + tier);
			}
			ctx.succeed();
		});
	}

	/** Spawns ten of a slot, kills them (by a mock player or not), then checks after the drops land. */
	static void killMany(GameTestHelper ctx, Slot slot, boolean byPlayer, Runnable check) {
		FormingTests.lightFrame(ctx, 2, 3);
		ctx.runAtTickTime(3, () -> killTen(ctx, slot, byPlayer, check));
	}

	/** Spawns and kills ten of a slot now, in a room whose rift is already lit, then checks after the drops land. */
	static void killTen(GameTestHelper ctx, Slot slot, boolean byPlayer, Runnable check) {
		var rift = FormingTests.riftIn(ctx);
		var player = StageTests.player(ctx);
		for (int i = 0; i < 10; i++) {
			for (var entity : RiftMobs.spawn(ctx.getLevel(), rift.id(), slot, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO)) {
				if (entity instanceof LivingEntity living) {
					var source = byPlayer ? ctx.getLevel().damageSources().playerAttack(player) : ctx.getLevel().damageSources().generic();
					living.hurtServer(ctx.getLevel(), source, 1000f);
				}
			}
		}
		ctx.runAfterDelay(5, () -> {
			check.run();
			StageTests.remove(ctx, player);
			ctx.killAllEntities();
			FormingTests.cleanUp(ctx);
			ctx.succeed();
		});
	}

	static int count(GameTestHelper ctx, Item item) {
		return ctx.getEntities(EntityTypes.ITEM).stream().filter(i -> i.getItem().is(item)).mapToInt(i -> i.getItem().getCount()).sum();
	}
}
