package nu.metacraft.resource_packs.mixin;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.resource_packs.PlayerManagerExtension;
import nu.metacraft.resource_packs.PlayerPackDataManager;

@Mixin(PlayerList.class)
public class PlayerListMixin implements PlayerManagerExtension {

	@Unique
	private PlayerPackDataManager manager;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(MinecraftServer server, LayeredRegistryAccess<?> registryManager, PlayerDataStorage saveHandler, NotificationService managementListener, CallbackInfo ci) {
		manager = new PlayerPackDataManager(server);
	}
	
	@Override
	public PlayerPackDataManager metacraft_resource_packs$getPlayerPackDataManager() {
		return manager;
	}

	@Inject(method = "save", at = @At("HEAD"))
	protected void savePlayerData(ServerPlayer player, CallbackInfo ci) {
		manager.save(player.getGameProfile());
	}
}
