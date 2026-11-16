package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.item.OvveFeet;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * The async mod ticks vanilla entities (mannequins, armour stands, mobs) in parallel on its own
 * threads, so an ovve's inventory tick runs on many threads at once. These tests do the same: each
 * worker ticks its own share of the wearers, as async hands each entity to one thread per tick.
 */
public final class AsyncTickTests {
	private static final int WEARERS = 64, THREADS = 8, ROUNDS = 200;

	/**
	 * Many mannequins in ovvar, ticked on 8 threads at once: the cuffs' per-viewer cache and the
	 * owners' wardrobe loads are shared by all of them. Forgetting a wearer between ticks (as a
	 * Danse gesture does) keeps the cache's entries coming and going, as players joining and wearers
	 * leaving do on a server.
	 */
	@GameTest(maxTicks = 400)
	public void ovveTicksOnManyThreadsAtOnce(GameTestHelper helper) {
		List<LivingEntity> wearers = new ArrayList<>();
		List<ItemStack> ovvar = new ArrayList<>();
		for (int i = 0; i < WEARERS; i++) {
			LivingEntity mannequin = helper.spawn(EntityTypes.MANNEQUIN, new BlockPos(1 + i % 4, 1, 1 + i / 16));
			ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
			OvveItem.setOwner(ovve, UUID.randomUUID());   // an owner nobody has loaded: every tick asks the store
			OvveItem.wear(mannequin, ovve);
			wearers.add(mannequin);
			ovvar.add(ovve);
		}
		Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
		CountDownLatch start = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (int t = 0; t < THREADS; t++) {
			int share = t;
			Thread thread = new Thread(() -> {
				try {
					start.await();
					for (int round = 0; round < ROUNDS; round++) {
						for (int i = share; i < WEARERS; i += THREADS) {
							ovvar.get(i).inventoryTick(helper.getLevel(), wearers.get(i), EquipmentSlot.LEGS);
							if (round % 2 == 1) OvveFeet.forget(wearers.get(i));
						}
					}
				} catch (Throwable e) {
					errors.add(e);
				}
			}, "ovvar-async-test-" + t);
			threads.add(thread);
			thread.start();
		}
		start.countDown();
		for (Thread thread : threads) {
			try {
				thread.join(60_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				helper.fail("interrupted waiting for the workers");
			}
		}
		if (!errors.isEmpty()) {
			Throwable first = errors.peek();
			helper.fail(errors.size() + " worker(s) failed, first: " + first + " at " + (first.getStackTrace().length > 0 ? first.getStackTrace()[0] : "?"));
		}
		helper.succeed();
	}
}
