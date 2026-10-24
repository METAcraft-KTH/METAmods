package se.metacraft.nether_rifts.mob;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import se.metacraft.nether_rifts.mixin.InteractionAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A hitbox a Chained Ghast carries along: a crew member ({@link CrewHitbox}) or its deck ({@link DeckHitbox}). Never
 * saved (the ghast makes them again), gone with the ghast, and passed through by the ghast's own fireballs and bombs.
 * Clients see an interaction: the deck's its size, invisible but there to hit; a crew member's zero wide, as they hit
 * its {@link HitStandIn}.
 */
public interface GhastPart {

	/** The ghast it belongs to; null before it joins one. */
	@Nullable
	WarGhast ghast();

	/**
	 * The tracked data an interaction this wide and as tall as the part gets: the base entity's (its flags: fire and so
	 * on) and the interaction's width and height. The part's own data would be read as the interaction's, so it doesn't go.
	 */
	static void asInteraction(Entity part, float width, List<SynchedEntityData.DataValue<?>> data, boolean initial) {
		data.removeIf(value -> value.id() >= InteractionAccessor.netherRifts$width().id());
		if (initial) {
			data.add(SynchedEntityData.DataValue.create(InteractionAccessor.netherRifts$width(), width));
			data.add(SynchedEntityData.DataValue.create(InteractionAccessor.netherRifts$height(), part.getBbHeight()));
		}
	}
}
