package se.metacraft.skins;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The skin an item carries while that application is live: the skin's current definition, and its
 * Owner. {@code perks} are the ones in effect: none while the settings switch all Perks off.
 * {@code dyeable}: the skin is a Dyeable Hat.
 * {@code name} is the skin's name without its formatting codes, {@code title} the name with them ({@link SkinName}).
 */
public record LiveSkin(String id, String name, Component title, Kind kind, boolean dyeable, Optional<String> description, Release release, List<Perk> perks,
						UUID owner, String ownerName) {}
