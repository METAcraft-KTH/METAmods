package se.metacraft.kultur.drinks;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * What the pack needs in the {@code minecraft} namespace for Inebriated, which a vanilla client is told
 * is Luck: PolymITer's icon for Luck and Luck's name, "Inebriated" ("Berusad"). Kept out of
 * {@code assets/} (Polymer copies all of that, in both modes) in {@code drinks_pack/assets/}, and added
 * only when the drinks are Kultur's, so with Brewery on the server Luck stays Luck.
 *
 * <p>Polymer merges every {@code assets/<namespace>/lang/*.json} key by key, so the two lang files add
 * their key to any other mod's {@code minecraft} lang files rather than replace them.
 */
public final class DrinksPack {
	public static final List<String> FILES = List.of(
			"assets/minecraft/textures/mob_effect/luck.png",
			"assets/minecraft/lang/en_us.json",
			"assets/minecraft/lang/sv_se.json");

	private DrinksPack() {}

	static void register() {
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(DrinksPack::addTo);
	}

	/** Adds the files to a pack being built. Public for the game tests. */
	public static void addTo(ResourcePackBuilder builder) {
		for (String path : FILES) builder.addData(path, read(path));
	}

	/** One of {@link #FILES}, as shipped. */
	public static byte[] read(String path) {
		try (InputStream in = DrinksPack.class.getResourceAsStream("/drinks_pack/" + path)) {
			if (in == null) throw new IllegalStateException("drinks_pack/" + path + " is missing from the jar");
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
