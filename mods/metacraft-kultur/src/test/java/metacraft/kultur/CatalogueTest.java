package metacraft.kultur;

import com.google.gson.JsonParser;
import metacraft.kultur.catalogue.Catalogue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalogueTest {
	@Test
	public void shippedCatalogueLoads() {
		Catalogue c = Catalogue.load();
		assertEquals(List.of("it", "data", "media"), c.chapters().stream().map(Catalogue.Chapter::id).toList());
		var patterns = c.patterns();
		assertEquals(List.of("itk", "qmisk", "tmeit", "pirkko"), patterns.stream().map(o -> o.value().id()).toList());
		assertTrue(patterns.stream().allMatch(o -> o.chapter().id().equals("it")));
		assertFalse(patterns.get(0).value().item(), "itk is free in the loom");
		assertTrue(patterns.get(3).value().item(), "pirkko needs its item");
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
}
