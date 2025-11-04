package nu.metacraft.lib.scheduler;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * A simple TimerCallback which does not persist on reboot.
 */
public class Throwaway implements TimerCallback<MinecraftServer> {

	public static final MapCodec<Throwaway> CODEC = MapCodec.unit(
			new Throwaway(() -> {})
	);

	private final Runnable runnable;

	public Throwaway(Runnable runnable) {
		this.runnable = runnable;
	}

	@Override
	public void handle(MinecraftServer server, TimerQueue<MinecraftServer> events, long time) {
		runnable.run();
	}

	@Override
	public MapCodec<? extends TimerCallback<MinecraftServer>> codec() {
		return CODEC;
	}
}
