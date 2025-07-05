package nu.metacraft.moderation.exile.rules;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public interface ZoneRule {
	void enterAllowedArea(ServerPlayerEntity player);
	void enterProhibitedArea(ServerPlayerEntity player);
	void tick(ServerPlayerEntity player);

	default Identifier getID() {
		return Optional.ofNullable(ZoneRuleRegistry.REGISTRY.getId(this)).orElse(Identifier.ofVanilla("missingno"));
	}
}
