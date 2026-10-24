package se.metacraft.lib;

import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The item namespaces whose items metacraft-core's creative tabs ("METAcraft" and "Spawn Eggs") list. Empty until a
 * mod opts in: a mod that registers items calls {@link #add} for their namespace, best from its item registration
 * class. Adding a namespace twice is fine, so mods sharing {@link METAcraftLib#NAMESPACE} each add it and stay
 * standalone. An item of such a namespace that no tab should list (a mob's kit, never meant to be handed out) is
 * {@link #hide hidden}.
 *
 * <p>It lives in metacraft-lib so mods that do not depend on metacraft-core can opt in too. The tabs read it when
 * they are shown, so a namespace added after metacraft-core initialised still counts.
 */
public final class CreativeTabNamespaces {

	private static final Set<String> NAMESPACES = ConcurrentHashMap.newKeySet();
	private static final Set<Item> HIDDEN = ConcurrentHashMap.newKeySet();

	private CreativeTabNamespaces() {}

	/** Lists the items of {@code namespace} in the creative tabs. */
	public static void add(String namespace) {
		NAMESPACES.add(namespace);
	}

	/** The namespaces opted in so far, as a read-only live view. */
	public static Set<String> get() {
		return Collections.unmodifiableSet(NAMESPACES);
	}

	public static boolean contains(String namespace) {
		return NAMESPACES.contains(namespace);
	}

	/** Keeps {@code item} out of the creative tabs, though its namespace is opted in. */
	public static void hide(Item item) {
		HIDDEN.add(item);
	}

	/** Whether the creative tabs list {@code item}: its namespace opted in, and it not hidden. */
	public static boolean lists(String namespace, Item item) {
		return NAMESPACES.contains(namespace) && !HIDDEN.contains(item);
	}

}
