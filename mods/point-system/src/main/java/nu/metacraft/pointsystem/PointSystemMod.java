package nu.metacraft.pointsystem;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;

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
            pointSystem = new PointSystem(server);
            try {
                pointSystem.loadConfig();
                pointSystem.loadData();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config.", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (pointSystem != null) {
                try {
                    pointSystem.saveData();
                } catch (Throwable e) {
                    LOGGER.error("Failed to save data", e);
                }
                pointSystem = null;
            }
        });
        ServerLifecycleEvents.AFTER_SAVE.register((minecraftServer, flush, force) -> {
            if (pointSystem != null) {
                try {
                    pointSystem.saveData();
                } catch (Throwable e) {
                    LOGGER.error("Failed to save data", e);
                }
            }
        });
    }

    public PointSystem getPointSystem(MinecraftServer server) {
        if (pointSystem != null && pointSystem.getServer() == server) {
            return pointSystem;
        }
        return null;
    }
}
