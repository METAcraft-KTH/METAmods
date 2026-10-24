package se.metacraft.ovvar.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.util.UndashedUuid;
import se.metacraft.ovvar.compat.resource_packs.ResourcePacksCompat;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.pack.EquipmentJson;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipInputStream;

/** Ovvar's own combination pack (pack/ComboPack), served by metacraft-resource-packs in the test runtime. */
public final class ComboPackTests {

	/** Patch ids may hold a '-'; a combination's key must still read back as the same combination. */
	@GameTest
	public void comboKeysKeepHyphenatedPatchIds(GameTestHelper helper) {
		Combos.Combo combo = Placement.combo(marshals(2));
		var back = Combos.Combo.KEY_CODEC.parse(JavaOps.INSTANCE, combo.key()).result();
		assertThat(back.isPresent(), "'" + combo.key() + "' does not read back");
		assertThat(back.get().key().equals(combo.key()), "'" + combo.key() + "' read back as '" + back.get().key() + "'");
		helper.succeed();
	}

	/** The pack holds pack.mcmeta and every chapter's definition of the combination, and the same set always hashes the same. */
	@GameTest
	public void theCombinationPackHoldsEveryChaptersDefinition(GameTestHelper helper) {
		Combos.Combo combo = Placement.combo(marshals(1));
		Set<Combos.KeyedCombo> combos = Set.of(new Combos.KeyedCombo(Piece.TOP, combo));
		ComboPack.Version version = ComboPack.build(combos);
		assertThat(version.sha1().equals(ComboPack.build(combos).sha1()), "the same combinations built twice hash differently");
		Map<String, String> files = unzip(version.zip());
		var meta = PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE,
				JsonParser.parseString(files.getOrDefault("pack.mcmeta", "{}")).getAsJsonObject().get(PackMetadataSection.CLIENT_TYPE.name()));
		assertThat(meta.isSuccess(), "pack.mcmeta does not read as a client pack: " + files.get("pack.mcmeta"));
		for (Chapter chapter : Chapter.values()) {
			String path = EquipmentJson.packPath(chapter, Piece.TOP, false, combo);
			assertThat(EquipmentJson.json(chapter, Piece.TOP, false, combo.placements()).equals(files.get(path)), "missing or wrong: " + path);
		}
		helper.succeed();
	}

	/** The newest build downloads from the pack server at the address a player is pushed, byte for byte; a pack it does not have is refused. */
	@GameTest(maxTicks = 200)
	public void theCombinationPackDownloadsFromThePackServer(GameTestHelper helper) {
		assertThat(ComboPack.ACTIVE, "metacraft-resource-packs is not in the test runtime");
		List<ComboPack.Version> pushed = new ArrayList<>();
		List<CompletableFuture<HttpResponse<byte[]>>> download = new ArrayList<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(ComboPack.current() != null, "no combination pack built yet"))
				.thenExecute(() -> {
					pushed.add(ComboPack.current());
					String url = ResourcePacksCompat.push(pushed.getFirst()).url();
					download.add(get(url));
					download.add(get(url.substring(0, url.lastIndexOf('/', url.lastIndexOf('/') - 1)) + "/" + UUID.randomUUID()));
				})
				.thenWaitUntil(() -> assertThat(download.stream().allMatch(CompletableFuture::isDone), "still downloading"))
				.thenExecute(() -> {
					HttpResponse<byte[]> response = download.getFirst().join();
					assertThat(response.statusCode() == 200, "the pack server answered " + response.statusCode());
					assertThat(Arrays.equals(response.body(), pushed.getFirst().zip()), "the download is not the pack that was pushed");
					assertThat(download.getLast().join().statusCode() == 403, "a pack the server does not have answered " + download.getLast().join().statusCode());
				})
				.thenSucceed();
	}

	/** A GET the way a client sends it; the pack server refuses anything else. */
	private static CompletableFuture<HttpResponse<byte[]>> get(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "Minecraft Java/26.3")
				.header("X-Minecraft-Username", "Tester")
				.header("X-Minecraft-UUID", UndashedUuid.toString(UUID.randomUUID()))
				.build();
		return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
	}

	/** {@code n} placements of tmeit-marshal (a hyphenated id) on the top. */
	private static List<Placement> marshals(int n) {
		Patches.Patch marshal = Patches.get("tmeit-marshal");
		List<Placement> out = new ArrayList<>();
		for (Spot spot : Spot.cells(Piece.TOP)) if (out.size() < n && marshal.fits(spot)) out.add(new Placement(spot, marshal));
		assertThat(out.size() == n, "tmeit-marshal fits fewer than " + n + " cells on the top");
		return out;
	}

	private static Map<String, String> unzip(byte[] zip) {
		Map<String, String> files = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return files;
	}

	private static void assertThat(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
	}
}
