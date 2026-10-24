package se.metacraft.nether_rifts;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletOpenState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/**
 * The guidebook's Unstable Portals page, written in metacraft-booklet. Booklet comes with metacraft-booklet, so its
 * classes are only touched once {@link #available()} says the guidebook is there.
 */
public final class RiftGuide {

	public static final Identifier PAGE = Identifier.fromNamespaceAndPath("metacraft", "rifts");

	private RiftGuide() {}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded("metacraft-booklet");
	}

	/** A click that opens the chapter. Only call it when {@link #available()}. */
	public static ClickEvent open() {
		return Booklet.open();
	}

	/** Kept apart so Booklet's classes load only when a link is actually made. */
	private static final class Booklet {
		static ClickEvent open() {
			return BookletImplUtil.encodeClickEvent("open_page", PAGE, BookletOpenState.DEFAULT);
		}
	}
}
