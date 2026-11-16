package metacraft.kultur;

import com.google.gson.JsonParser;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.datagen.ArtFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalogueTest {
	@Test
	public void shippedCatalogueLoads() {
		Catalogue c = Catalogue.load();
		var itPatterns = c.chapters().stream().filter(x -> x.id().equals("it")).findFirst().orElseThrow().patterns();
		assertEquals(List.of("itk", "qmisk", "tmeit", "pirkko", "it"), itPatterns.stream().map(Catalogue.Pattern::id).toList());
		for (int i = 0; i < 4; i++) assertTrue(itPatterns.get(i).item(), itPatterns.get(i).id() + " needs its item");
		assertFalse(itPatterns.get(4).item(), "it is free in the loom");
		var paintings = c.paintings();
		assertEquals(1, paintings.size());
		Catalogue.Painting draken = paintings.getFirst().value();
		assertEquals("draken", draken.id());
		assertEquals("The Guardian of Kistan", draken.title());
		assertEquals("Emelie Stark", draken.author());
		assertEquals(3, draken.width());
		assertEquals(4, draken.height());
	}

	@Test
	public void itemDefaultsToFalseAndListsToEmpty() {
		Catalogue c = Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "a", "name": "A"}]}]}"""));
		assertFalse(c.patterns().getFirst().value().item());
		assertTrue(c.paintings().isEmpty());
	}

	@Test
	public void duplicateIdsAreRefused() {
		var e = assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [
					{"id": "x", "name": "X", "patterns": [{"id": "a", "name": "A"}]},
					{"id": "y", "name": "Y", "patterns": [{"id": "a", "name": "A again"}]}]}""")));
		assertTrue(e.getMessage().contains("a"), e.getMessage());
	}

	@Test
	public void blankNamesAreRefused() {
		assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "a", "name": ""}]}]}""")));
	}

	@Test
	public void badIdsAreRefused() {
		assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "Släggan", "name": "S"}]}]}""")));
		assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "paintings": [{"id": "p", "title": "T", "author": "A", "width": 0, "height": 1}]}]}""")));
	}

	@Test
	public void lootNeedsItem() {
		var e = assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "x", "name": "X", "loot": true}]}]}""")));
		assertTrue(e.getMessage().contains("has loot but no item"), e.getMessage());
	}

	/** Edvin, 2026-11: every chapter's logo is free in the loom; only the IT clubs' patterns and Pirkko have an item, none in chests. */
	@Test
	public void everyChapterHasItsFreeLogo() {
		Catalogue c = Catalogue.load();
		List<String> ids = List.of("arkitektur", "bergs", "bygg", "cl", "data", "doktorand", "elektro", "energi", "flyg", "fysik",
				"it", "kemi", "maskin", "media", "mit", "open", "produktion", "samhall", "tbas", "indek");
		assertEquals(ids.stream().sorted().toList(), c.chapters().stream().map(Catalogue.Chapter::id).sorted().toList());
		for (Catalogue.Chapter chapter : c.chapters()) {
			Catalogue.Pattern logo = chapter.patterns().stream().filter(p -> p.id().equals(chapter.id())).findFirst().orElse(null);
			assertTrue(logo != null, chapter.id() + " has no pattern of its own id");
			assertFalse(logo.item(), chapter.id() + " logo needs an item");
		}
		List<String> withItem = c.patterns().stream().map(Catalogue.Owned::value).filter(Catalogue.Pattern::item).map(Catalogue.Pattern::id).toList();
		assertEquals(List.of("itk", "qmisk", "tmeit", "pirkko"), withItem);
		assertTrue(c.patterns().stream().noneMatch(p -> p.value().loot()), "no pattern is in chests for now");
	}

	@Test
	@EnabledIfSystemProperty(named = "kultur.release", matches = "true")
	public void noPlaceholderArt() throws IOException {
		List<String> left = new ArrayList<>();
		Path root = Path.of("src/main/resources/art/kultur");
		try (var files = Files.walk(root)) {
			for (Path f : files.filter(f -> f.toString().endsWith(".png")).toList()) {
				String name = f.getFileName().toString();
				// A "_x.png" beside an "x.png" is ignored by datagen, so it cannot ship as art.
				if (name.startsWith("_") && Files.exists(f.resolveSibling(name.substring(1)))) continue;
				var img = ImageIO.read(f.toFile());
				if (img != null && img.getRGB(0, 0) == 0xFFFF00FF) left.add(root.relativize(f).toString());
			}
		}
		assertTrue(left.isEmpty(), "placeholder art still in: " + String.join(", ", left));
	}

	@Test
	public void underscoreArtIsUsed() {
		assertEquals("_x.png", ArtFiles.fileFor("x", Set.of("_x.png")::contains, m -> {}));
		assertEquals(null, ArtFiles.fileFor("x", Set.of("y.png")::contains, m -> {}));
	}

	@Test
	public void plainArtWinsOverUnderscore() {
		List<String> logged = new ArrayList<>();
		assertEquals("x.png", ArtFiles.fileFor("x", Set.of("x.png", "_x.png")::contains, logged::add));
		assertEquals(1, logged.size());
		assertTrue(logged.get(0).contains("_x.png is ignored"));
	}
}
