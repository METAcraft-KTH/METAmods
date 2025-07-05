package nu.metacraft.lib.util;

import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.util.impl.TaskSchedulerImpl;

import java.util.function.Consumer;

public interface TaskScheduler {

	static void scheduleImmediately(MinecraftServer server, Runnable toRun) {
		TaskSchedulerImpl.scheduleImmediately(server, toRun);
	}

	static void schedule(MinecraftServer server, Runnable toRun, int afterTicks) {
		TaskSchedulerImpl.schedule(server, toRun, afterTicks);
	}

	static void scheduleImmediatelyForAll(Consumer<MinecraftServer> toRun) {
		TaskSchedulerImpl.scheduleImmediatelyForAll(toRun);
	}

	static void scheduleForAll(Consumer<MinecraftServer> toRun, int afterTicks) {
		TaskSchedulerImpl.scheduleForAll(toRun, afterTicks);
	}

}
