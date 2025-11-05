package nu.metacraft.resource_packs;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.function.Consumer;

public interface EarlyPacksCallback {

	Event<EarlyPacksCallback> EVENT = EventFactory.createArrayBacked(
			EarlyPacksCallback.class,
			events -> (server, profile, playerData, resourcePackAdder) -> {
				for (var event : events) {
					event.addPacks(server, profile, playerData, resourcePackAdder);
				}
			}
	);

	void addPacks(MinecraftServer server, GameProfile profile, CompoundTag playerData, Consumer<UUID> resourcePackAdder);

}
