package se.datasektionen.mc.metacraft_lib.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TaskScheduler {

	private static final ThreadLocal<Set<Task>> tasks = ThreadLocal.withInitial(HashSet::new);

	public static void scheduleNextTick(Runnable toRun) {
		scheduleNextTick(server -> toRun.run());
	}

	public static void schedule(Runnable toRun, int afterTicks) {
		schedule(server -> toRun.run(), afterTicks);
	}

	public static void scheduleNextTick(Consumer<MinecraftServer> toRun) {
		schedule(toRun, 1);
	}

	public static void schedule(Consumer<MinecraftServer> toRun, int afterTicks) {
		tasks.get().add(new Task(toRun, new MutableInt(afterTicks)));
	}

	public record Task(Consumer<MinecraftServer> action, MutableInt time) {}

	private static void runEvents(MinecraftServer server) {
		var it = tasks.get().iterator();
		while (it.hasNext()) {
			var task = it.next();
			if (task.time().getAndDecrement() == 0) {
				task.action().accept(server);
				it.remove();
			}
		}
	}

	static {
		ServerTickEvents.END_SERVER_TICK.register(TaskScheduler::runEvents);
	}

}
