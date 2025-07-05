package nu.metacraft.lib.extensions;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.UUID;

public interface TradeOfferExtensions {
	int metacraft$getMaxUsesPerPlayer();
	void metacraft$setMaxUsesPerPlayer(int maxUsesPerPlayer);
	Object2IntMap<UUID> metacraft$getUsesPerPlayer();
	void metacraft$setUses(int uses);
	void metacraft$setMaxUses(int maxUses);
}
