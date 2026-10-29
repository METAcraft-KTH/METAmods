package se.metacraft.skins;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * One skin folder as {@code /itemskins list} shows it: its id, its name and Kind (the name only once
 * it loads; the Kind when the definition got far enough to name a known one), the Materials it's
 * limited to (once it loads, in vanilla order; empty if it fits every one), whether it's a Dyeable
 * Hat (once it loads), its optional description, {@link Release} and Perks (once it loads; no Perks
 * while the settings switch them all off) and, for a Dormant skin, why it didn't load. {@code name}
 * is the name without its formatting codes, {@code title} the name with them ({@link SkinName}).
 */
public record SkinSummary(String id, Optional<String> name, Optional<Component> title, Optional<Kind> kind, Optional<List<Material>> materials,
							boolean dyeable, Optional<String> description, Optional<Release> release, List<Perk> perks,
							Optional<String> dormantReason) {
	public boolean dormant() {
		return dormantReason.isPresent();
	}

	/** What the skin fits, once its Kind is known. */
	public Optional<Fit> fit() {
		return kind.map(k -> new Fit(k, materials));
	}

	/** What players read: the name, or the id while the skin is Dormant. */
	public String displayName() {
		return name.orElse(id);
	}

	/** The name as menus show it, in its colours ({@link SkinName}); the id, yellow, while the skin is Dormant. */
	public Component styledName() {
		return title.orElseGet(() -> SkinName.styled(id));
	}
}
