package se.metacraft.skins;

import java.time.Instant;
import java.util.Optional;

/** One skin a player owns, as {@code /itemskins info} shows it: its id, when it was claimed (or granted), its state, and a Dyeable Hat's saved {@link Tint}. */
public record OwnedSkin(String id, Instant claimed, State state, Optional<Tint> tint) {
	public enum State {
		/** On an item in this world. */
		APPLIED("Applied"),
		/** On no item: never applied, removed, or overwritten. */
		UNAPPLIED("Unapplied"),
		/** Its application was stamped by another world (a copied {@code skins-data/} on a fresh world), so here it is on no item. */
		APPLIED_IN_ANOTHER_WORLD("Applied in another world"),
		/** Its definition is missing or failed to load; the record is kept and comes back with it. */
		DORMANT("Dormant");

		public final String label;

		State(String label) {
			this.label = label;
		}
	}
}
