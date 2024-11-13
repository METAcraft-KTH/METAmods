package se.datasektionen.mc.metacraft_lib.util.impl;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TaskSchedulerImpl {

	private static final Map<MinecraftServer, TaskContainer> tasks = new ConcurrentHashMap<>();

	public static void scheduleImmediately(MinecraftServer server, Runnable toRun) {
		schedule(server, toRun, 1);
	}

	public static void schedule(MinecraftServer server, Runnable toRun, int afterTicks) {
		tasks.computeIfAbsent(server, s -> new TaskContainer()).add(new Task(toRun, new MutableInt(afterTicks)));
	}

	public static void scheduleImmediatelyForAll(Consumer<MinecraftServer> toRun) {
		scheduleForAll(toRun, 1);
	}

	public static void scheduleForAll(Consumer<MinecraftServer> toRun, int afterTicks) {
		tasks.keySet().forEach(
				server -> schedule(server, () -> toRun.accept(server), afterTicks)
		);
	}

	public record Task(Runnable action, MutableInt time) {}

	private static void runEvents(MinecraftServer server) {
		var taskList = tasks.get(server);
		if (taskList != null) {
			taskList.tick();
		}
	}

	public static class TaskContainer {
		private final Lock lock = new ReentrantLock();
		private final Set<Task> tasks = new HashSet<>();
		private final List<Task> newTasks = new ArrayList<>();

		private TaskContainer() {}

		public void tick() {
			lock.lock();
			try {
				tasks.addAll(newTasks);
				newTasks.clear();
				tasks.removeIf(task -> {
					if (task.time.decrementAndGet() == 0) {
						task.action.run();
						return true;
					}
					return false;
				});
			} finally {
				lock.unlock();
			}
		}

		public void add(Task task) {
			lock.lock();
			try {
				newTasks.add(task);
			} finally {
				lock.unlock();
			}
		}

	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(
				tasks::remove
		);
		ServerTickEvents.END_SERVER_TICK.register(TaskSchedulerImpl::runEvents);
	}

}
