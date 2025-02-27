package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.stat.ServerStatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(PlayerManager.class)
public interface AccessorPlayerManager {

	@Accessor
	Map<UUID, ServerStatHandler> getStatisticsMap();

	@Accessor
	Map<UUID, PlayerAdvancementTracker> getAdvancementTrackers();

}
