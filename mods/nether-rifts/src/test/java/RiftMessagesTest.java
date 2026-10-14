import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.RiftGuide;
import nu.metacraft.nether_rifts.RiftMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a player near a new rift reads, and where its link goes. */
public class RiftMessagesTest {

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** Every click event in a message and its siblings, depth first. */
	static List<ClickEvent> clicks(Component message) {
		var out = new ArrayList<ClickEvent>();
		if (message.getStyle().getClickEvent() != null) {
			out.add(message.getStyle().getClickEvent());
		}
		for (var sibling : message.getSiblings()) {
			out.addAll(clicks(sibling));
		}
		return out;
	}

	@Test
	public void linkOpensTheRiftsChapter() {
		assertEquals(Identifier.fromNamespaceAndPath("metacraft", "rifts"), RiftGuide.PAGE);
		var clicks = clicks(RiftMessages.warning(false, true));
		assertEquals(1, clicks.size(), "one link: " + clicks);
		var click = assertInstanceOf(ClickEvent.Custom.class, clicks.getFirst());
		assertEquals(Identifier.fromNamespaceAndPath("booklet", "open_page"), click.id());
		var entry = click.payload().map(tag -> (CompoundTag) tag).flatMap(tag -> tag.getString("entry"));
		assertEquals(Optional.of("metacraft:rifts"), entry);
	}

	@Test
	public void noGuidebookNoLink() {
		var message = RiftMessages.warning(false, false);
		assertTrue(clicks(message).isEmpty(), "no link without metacraft-booklet: " + clicks(message));
		assertFalse(message.getString().contains("[Read more]"), message.getString());
		assertTrue(message.getString().contains("The realm is unstable."), message.getString());
	}

	@Test
	public void cryingRiftHasItsOwnWarning() {
		var text = RiftMessages.warning(true, true).getString();
		assertFalse(text.contains("unstable"), text);
		assertTrue(text.contains("crying obsidian"), text);
		assertEquals(1, clicks(RiftMessages.warning(true, true)).size());
	}
}
