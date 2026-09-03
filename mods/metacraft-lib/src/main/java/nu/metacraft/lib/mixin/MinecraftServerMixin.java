package nu.metacraft.lib.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import nu.metacraft.lib.extensions.ServerAndServerLevelExtensions;
import nu.metacraft.lib.util.PotentialPlayer;
import nu.metacraft.lib.util.SavedDataTypeCache;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.TestHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements ServerAndServerLevelExtensions<MinecraftServer> {

	@Shadow
	public abstract RegistryAccess.Frozen registryAccess();

	@Inject(method = "stopServer", at = @At("HEAD"), cancellable = true)
	public void onShutdown(CallbackInfo ci) {
		if ((Object) this instanceof GameTestServer && TestHelper.isJunit()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleCustomClickAction", at = @At("RETURN"))
	public void handleCustomClickAction(
			Identifier id,
			@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload,
			CallbackInfo ci
	) {
		CustomMessageRegistry.handleAction(id, payload, (MinecraftServer) (Object) this, PotentialPlayer.empty());
	}

	@Unique
	private final Map<SavedDataTypeCache.Type<?, MinecraftServer>, SavedDataType<?>> typeMap = new HashMap<>();

	@SuppressWarnings("unchecked")
	@Override
	public <T extends SavedData> SavedDataType<@NotNull T> metacraft$getSavedDataType(SavedDataTypeCache.Type<T, MinecraftServer> type) {
		return (SavedDataType<@NotNull T>) typeMap.computeIfAbsent(
				type, t -> t.createSavedDataType().apply((MinecraftServer) (Object) this)
		);
	}

}
