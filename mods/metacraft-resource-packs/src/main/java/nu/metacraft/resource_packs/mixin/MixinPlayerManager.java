package nu.metacraft.resource_packs.mixin;

import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.dedicated.management.listener.ManagementListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PlayerSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.resource_packs.PlayerManagerExtension;
import nu.metacraft.resource_packs.PlayerPackDataManager;

@Mixin(PlayerManager.class)
public class MixinPlayerManager implements PlayerManagerExtension {

	@Unique
	private PlayerPackDataManager manager;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(MinecraftServer server, CombinedDynamicRegistries<?> registryManager, PlayerSaveHandler saveHandler, ManagementListener managementListener, CallbackInfo ci) {
		manager = new PlayerPackDataManager(server);
	}
	
	@Override
	public PlayerPackDataManager metacraft_resource_packs$getPlayerPackDataManager() {
		return manager;
	}

	@Inject(method = "savePlayerData", at = @At("HEAD"))
	protected void savePlayerData(ServerPlayerEntity player, CallbackInfo ci) {
		manager.save(player.getGameProfile());
	}
}
