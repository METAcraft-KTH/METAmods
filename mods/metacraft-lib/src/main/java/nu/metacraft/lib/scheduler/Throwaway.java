package nu.metacraft.lib.scheduler;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.timer.Timer;
import net.minecraft.world.timer.TimerCallback;

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
	public void call(MinecraftServer server, Timer<MinecraftServer> events, long time) {
		runnable.run();
	}

	@Override
	public MapCodec<? extends TimerCallback<MinecraftServer>> getCodec() {
		return CODEC;
	}
}
