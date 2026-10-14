package nu.metacraft.nether_rifts.mob;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import nu.metacraft.nether_rifts.mixin.InteractionAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A hitbox a Chained Ghast carries along: a crew member ({@link CrewHitbox}) or its deck ({@link DeckHitbox}). Never
 * saved (the ghast makes them again), gone with the ghast, and passed through by the ghast's own fireballs and bombs.
 * Clients see an interaction of the same size: invisible, but there to hit, as BIL shows the ghast's own box.
 */
public interface GhastPart {

	/** The ghast it belongs to; null before it joins one. */
	@Nullable
	WarGhast ghast();

	/**
	 * The tracked data an interaction of the part's size gets: the base entity's (its flags: fire and so on) and the
	 * interaction's width and height. The part's own data would be read as the interaction's, so it doesn't go.
	 */
	static void asInteraction(Entity part, List<SynchedEntityData.DataValue<?>> data, boolean initial) {
		data.removeIf(value -> value.id() >= InteractionAccessor.netherRifts$width().id());
		if (initial) {
			data.add(SynchedEntityData.DataValue.create(InteractionAccessor.netherRifts$width(), part.getBbWidth()));
			data.add(SynchedEntityData.DataValue.create(InteractionAccessor.netherRifts$height(), part.getBbHeight()));
		}
	}
}
