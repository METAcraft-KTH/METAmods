package nu.metacraft.lib.callbacks;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;

import java.util.Arrays;
import java.util.Optional;

public interface GetScoreboardCallback {

	Event<GetScoreboardCallback> EVENT = EventFactory.createArrayBacked(
			GetScoreboardCallback.class, events ->
					player -> Arrays.stream(events).reduce(
							Optional.empty(),
							(lhs, rhs) -> lhs.or(() -> rhs.getScoreboard(player)),
							(lhs, rhs) -> lhs.or(() -> rhs)
					)
	);

	Optional<Scoreboard> getScoreboard(ServerPlayer player);

}
