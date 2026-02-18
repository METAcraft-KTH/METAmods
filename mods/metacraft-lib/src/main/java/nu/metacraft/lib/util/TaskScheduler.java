package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerCallbacks;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.scheduler.Named;
import nu.metacraft.lib.scheduler.Throwaway;
import nu.metacraft.lib.util.helper.RegistryDependentCodecHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface TaskScheduler {

	/**
	 * Schedules an event to be executed at the end of this game tick.
	 * Useful for those edge-cases where you want to execute code this tick,
	 * but it's not safe to execute it immediately.
	 *
	 * @param server The server to execute the task on.
	 * @param toRun The task to execute.
	 */
	static void scheduleImmediately(MinecraftServer server, Runnable toRun) {
		server.scheduleWithResult(future -> {
			toRun.run();
			future.complete(Unit.INSTANCE);
		});
	}

	/**
	 * Schedules a throwaway event on Mojang's scheduler.
	 * This event will not persist after a server reboot.
	 * Is generally intended for unimportant events such as particle effects or if you know this function will be called again after the restart anyway.
	 * Note, since each event is given a random UUID, it is possible (but unlikely) that your event will never execute because it was overwritten by another event.
	 * If this worries you, use {@link TaskScheduler#schedule(MinecraftServer, Identifier, TimerCallback, int)}
	 * instead (with a {@link Throwaway}) and provide your own uniquely generated name.
	 *
	 * @param server The server to execute the task on.
	 * @param toRun The action to run.
	 * @param afterTicks How many ticks to wait before executing the event.
	 */
	static void scheduleThrowaway(
			MinecraftServer server, Runnable toRun, int afterTicks
	) {
		schedule(
				server,
				METAcraftLib.getID("throwaway/" + UUID.randomUUID()),
				new Throwaway(toRun),
				afterTicks
		);
	}

	/**
	 * Schedules the given task to Mojang's scheduler.
	 * Each callback type you wish to run should be registered with
	 * {@link TaskScheduler#registerTaskType(Identifier, MapCodec)}.
	 *
	 * @param server The server to execute the task on.
	 * @param name The name of the task. Must be unique.
	 * @param toRun The task to run.
	 * @param afterTicks How many ticks to wait before running the task.
	 */
	static void schedule(
			MinecraftServer server, Identifier name,
			TimerCallback<MinecraftServer> toRun, int afterTicks
	) {
		server.getScheduledEvents().schedule(
				name.toString(),
				afterTicks + server.overworld().getGameTime(),
				toRun
		);
	}

	/**
	 * Schedules the given task to Mojang's scheduler.
	 * Each callback type you wish to run should be registered with
	 * {@link TaskScheduler#registerTaskType(Identifier, MapCodec)}.
	 * Unlike {@link TaskScheduler#schedule(MinecraftServer, Identifier, TimerCallback, int)},
	 * this function does not require a name. The name is instead fetched from the task directly.
	 *
	 * @param server The server to execute the task on.
	 * @param toRun A named task to run.
	 * @param afterTicks How many ticks to wait.
	 * @param <T> The type of the task.
	 */
	static <T extends TimerCallback<MinecraftServer> & Named> void schedule(
			MinecraftServer server,
			T toRun, int afterTicks
	) {
		server.getScheduledEvents().schedule(
				toRun.getName(),
				afterTicks + server.overworld().getGameTime(),
				toRun
		);
	}

	/**
	 * Registers the given codec to Mojang's scheduler.
	 * This allows events using that codec to be serialized and deserialized.
	 * @param id The id of the event type.
	 * @param codec The codec for the event type.
	 */
	static void registerTaskType(
			Identifier id,
			MapCodec<? extends TimerCallback<MinecraftServer>> codec
	) {
		TimerCallbacks.SERVER_CALLBACKS.register(id, codec);
	}

}
