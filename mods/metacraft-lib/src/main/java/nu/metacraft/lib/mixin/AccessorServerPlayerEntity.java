package nu.metacraft.lib.mixin;

import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public interface AccessorServerPlayerEntity {

	@Accessor
	@Mutable
	void setAdvancementTracker(PlayerAdvancementTracker advancementTracker);

	@Accessor
	@Mutable
	void setStatHandler(ServerStatHandler statHandler);

	@Invoker
	static GameMode callGameModeFromData(@Nullable ReadView view, String key) {
		throw new IllegalStateException("Broken Mixin");
	}

	@Invoker
	static Optional<ServerPlayerEntity.RespawnPos> callFindRespawnPosition(
			ServerWorld world, ServerPlayerEntity.Respawn respawn, boolean drainRespawnAnchor
	) {
		throw new MixinError("Error");
	}

}
