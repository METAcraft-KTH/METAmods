package nu.metacraft.moderation.exile.rules;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface ZoneRule {
	void enterAllowedArea(ServerPlayer player);
	void enterProhibitedArea(ServerPlayer player);
	void tick(ServerPlayer player);

	default Identifier getID() {
		return Optional.ofNullable(ZoneRuleRegistry.REGISTRY.getKey(this)).orElse(Identifier.withDefaultNamespace("missingno"));
	}
}
