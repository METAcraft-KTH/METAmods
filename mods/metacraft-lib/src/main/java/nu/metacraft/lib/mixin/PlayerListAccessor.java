package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;

@Mixin(PlayerList.class)
public interface PlayerListAccessor {

	@Accessor
	Map<UUID, ServerStatsCounter> getStats();

	@Accessor
	Map<UUID, PlayerAdvancements> getAdvancements();

}
