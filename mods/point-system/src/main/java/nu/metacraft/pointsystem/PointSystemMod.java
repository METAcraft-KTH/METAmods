package nu.metacraft.pointsystem;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public class PointSystemMod implements ModInitializer {
	public static final Logger LOGGER = LogUtils.getLogger();
	private PointSystem pointSystem;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, env) -> {
			new PointSystemCommand(this).register(dispatcher);
			new ChangeUniversityCommand(this).register(dispatcher);
		}));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PointSystemConfig.getInstance();
			pointSystem = new PointSystem(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (pointSystem != null) {
				try {
					pointSystem.close();
				} catch (Throwable e) {
					LOGGER.error("Failed to properly close database connection", e);
				}
				pointSystem = null;
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			pointSystem.updatePlayerScore(player);
		});
	}

	public PointSystem getPointSystem(MinecraftServer server) {
		if (pointSystem != null && pointSystem.getServer() == server) {
			return pointSystem;
		}
		return null;
	}
}
