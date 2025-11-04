package nu.metacraft.lib.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.Optional;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ValueInput;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {

	@Accessor
	@Mutable
	void setAdvancements(PlayerAdvancements advancementTracker);

	@Accessor
	@Mutable
	void setStats(ServerStatsCounter statHandler);

	@Invoker
	static GameType callReadPlayerMode(@Nullable ValueInput view, String key) {
		throw new IllegalStateException("Broken Mixin");
	}

	@Invoker
	static Optional<ServerPlayer.RespawnPosAngle> callFindRespawnAndUseSpawnBlock(
			ServerLevel world, ServerPlayer.RespawnConfig respawn, boolean drainRespawnAnchor
	) {
		throw new MixinError("Error");
	}

}
