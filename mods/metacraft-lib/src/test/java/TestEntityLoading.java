import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import se.metacraft.lib.METAcraftLib;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@code ServerLevelMixin} nudges chunks a spawning player waits on into loading their entities.
 * A chunk that is already entity-ticking, with its entities still on their way from disk, must stay
 * ticking: a game test whose chunk lost that never starts, and the whole run hangs.
 */
public class TestEntityLoading {

	public static void init() {
		Registry.register(
				BuiltInRegistries.TEST_FUNCTION,
				METAcraftLib.getID(TestInit.TEST_PREFIX + "entity-loading/waiting-keeps-ticking"),
				TestEntityLoading::waitingKeepsTicking
		);
	}

	private static void waitingKeepsTicking(GameTestHelper ctx) {
		ServerLevel level = ctx.getLevel();
		var origin = ChunkPos.containing(ctx.absolutePos(BlockPos.ZERO));
		// A chunk nothing has loaded yet, far from every test.
		var chunk = new ChunkPos(origin.x() + 64, origin.z());
		var held = new CountDownLatch(1);
		// Hold up the entity storage's IO thread, the way a busy machine does, so the chunk's
		// entities are still being read when the wait starts.
		entityStorage(level).write(new ChunkPos(origin.x() + 128, origin.z()), () -> {
			try {
				held.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null;
		});
		try {
			level.setChunkForced(chunk.x(), chunk.z(), true);
			level.getServer().managedBlock(() -> level.isPositionEntityTicking(chunk.getMiddleBlockPosition(64)));
			ctx.assertFalse(level.areEntitiesLoaded(chunk.pack()), "the chunk's entities loaded while the storage was held up");

			Thread.ofVirtual().start(() -> {
				try {
					Thread.sleep(200);
				} catch (InterruptedException ignored) {
				}
				held.countDown();
			});
			// What a player spawning next to the chunk does.
			level.waitForEntities(chunk, 0);

			ctx.assertTrue(level.areEntitiesActuallyLoadedAndTicking(chunk), "the chunk stopped ticking while a spawn waited for its entities");
			ctx.succeed();
		} finally {
			held.countDown();
			level.setChunkForced(chunk.x(), chunk.z(), false);
		}
	}

	private static SimpleRegionStorage entityStorage(ServerLevel level) {
		try {
			var manager = field(level, ServerLevel.class, "entityManager");
			var storage = field(manager, manager.getClass(), "permanentStorage");
			return (SimpleRegionStorage) field(storage, storage.getClass(), "simpleRegionStorage");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object field(Object owner, Class<?> type, String name) throws ReflectiveOperationException {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

}
