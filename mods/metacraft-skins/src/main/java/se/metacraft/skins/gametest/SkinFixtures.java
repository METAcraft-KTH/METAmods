package se.metacraft.skins.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import se.metacraft.skins.ReloadReport;
import se.metacraft.skins.Skins;
import se.metacraft.skins.SkinsPack;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/**
 * A server root of its own for one game test: a temporary directory holding
 * {@code config/metacraft-skins/skins/}, where a test writes skin folders the way an admin would.
 */
final class SkinFixtures {
	final Path root;
	final Skins skins;

	private SkinFixtures(Path root) {
		this.root = root;
		this.skins = Skins.at(root);
	}

	static SkinFixtures create() {
		try {
			return new SkinFixtures(Files.createTempDirectory("metacraft-skins-test"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	Path folder(String id) {
		return root.resolve("config/metacraft-skins/skins").resolve(id);
	}

	/** Writes one file of a skin folder, creating the folder. */
	SkinFixtures file(String id, String name, String text) {
		return file(id, name, text.getBytes(StandardCharsets.UTF_8));
	}

	SkinFixtures file(String id, String name, byte[] bytes) {
		try {
			Files.createDirectories(folder(id));
			Files.write(folder(id).resolve(name), bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** A texture: its bytes only need to be recognisable, the mod copies them untouched. */
	SkinFixtures png(String id, String name) {
		return file(id, name + ".png", pngBytes(id, name));
	}

	static byte[] pngBytes(String id, String name) {
		return ("png:" + id + "/" + name).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * A Look-only skin as a Blockbench export gives it: one model whose texture is a bare name
	 * (Blockbench's habit), with every display transform present, and its texture.
	 */
	SkinFixtures lookOnly(String id, String kind) {
		return file(id, "skin.json5", "{ name: \"" + id + " skin\", kind: \"" + kind + "\", look: { model: \"" + id + ".json\" } }")
				.file(id, id + ".json", model(id))
				.png(id, id);
	}

	/** A valid Look-only skin of any Kind, a spear's two models included, named and described as given. */
	SkinFixtures skin(String id, String kind, String name, String description) {
		String desc = description == null ? "" : ", description: \"" + description + "\"";
		if (!kind.equals("spear")) {
			return file(id, "skin.json5", "{ name: \"" + name + "\", kind: \"" + kind + "\", look: { model: \"" + id + ".json\" }" + desc + " }")
					.file(id, id + ".json", model(id))
					.png(id, id);
		}
		return file(id, "skin.json5", "{ name: \"" + name + "\", kind: \"spear\", look: { icon: \"" + id + "_icon.json\", in_hand: \"" + id + "_hand.json\" }"
				+ desc + " }")
				.file(id, id + "_icon.json", model(id))
				.file(id, id + "_hand.json", model(id))
				.png(id, id);
	}

	/**
	 * A Look-only skin (not a spear) named "<id> skin", limited to Materials as written in
	 * {@code skin.json5}: {@code materials} is the json5 value, e.g. {@code ["leather", "turtle"]}.
	 */
	SkinFixtures limited(String id, String kind, String materials) {
		return file(id, "skin.json5", "{ name: \"" + id + " skin\", kind: \"" + kind + "\", look: { model: \"" + id + ".json\" }, materials: "
				+ materials + " }")
				.file(id, id + ".json", model(id))
				.png(id, id);
	}

	/**
	 * A valid skin (not a spear) named "<id> skin" with these Perks, written as an admin would:
	 * {@code perks} is the json5 list, e.g. {@code [ { trigger: "held", effects: [ ... ] } ]}.
	 */
	SkinFixtures perky(String id, String kind, String perks) {
		return perky(id, kind, id + " skin", null, perks);
	}

	/** {@link #perky(String, String, String)} named and described as given. */
	SkinFixtures perky(String id, String kind, String name, String description, String perks) {
		String desc = description == null ? "" : ", description: \"" + description + "\"";
		return file(id, "skin.json5", "{ name: \"" + name + "\", kind: \"" + kind + "\", look: { model: \"" + id + ".json\" }" + desc
				+ ", perks: " + perks + " }")
				.file(id, id + ".json", model(id))
				.png(id, id);
	}

	/**
	 * A hat (helmet) named "<id> skin" whose model marks Tint faces, with real textures: texture
	 * {@code 0} ({@code felt.png}) is tinted on the north face ({@code tintindex} 1) and untinted on the
	 * south (no {@code tintindex}) and east ({@code -1}) faces, and texture {@code 1} ({@code band.png})
	 * is never tinted. {@code look} is the json5 look object, e.g. {@code { model: "<id>.json" }}.
	 */
	SkinFixtures tintedHat(String id, String look) {
		return hat(id, look, "");
	}

	/** {@link #tintedHat} as a Dyeable Hat: {@code look: { model, dyeable: true }}, fitting every helmet. */
	SkinFixtures dyeableHat(String id) {
		return hat(id, "{ model: \"" + id + ".json\", dyeable: true }", "");
	}

	/** {@link #dyeableHat(String)} limited to Materials: {@code materials} is the json5 list, e.g. {@code ["leather"]}. */
	SkinFixtures dyeableHat(String id, String materials) {
		return hat(id, "{ model: \"" + id + ".json\", dyeable: true }", ", materials: " + materials);
	}

	private SkinFixtures hat(String id, String look, String more) {
		return file(id, "skin.json5", "{ name: \"" + id + " skin\", kind: \"helmet\", look: " + look + more + " }")
				.file(id, id + ".json", TINTED_MODEL)
				.file(id, "felt.png", png(2, 0xFF8A5A3C, 0xFF6E4A30))
				.file(id, "band.png", png(1, 0xFF281C16));
	}

	/** A real PNG, {@code width} pixels wide, of these ARGB pixels row by row. */
	static byte[] png(int width, int... argb) {
		BufferedImage image = new BufferedImage(width, argb.length / width, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, argb.length / width, argb, 0, width);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** A PNG's pixels as ARGB, row by row. */
	static int[] pixels(byte[] png) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
			if (image == null) throw fail("not a PNG");
			return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A Skin Box file, {@code config/metacraft-skins/boxes/<name>}, as an admin writes it (name usually {@code <id>.json5}). */
	SkinFixtures box(String name, String json5) {
		try {
			Files.createDirectories(skins.boxesFolder());
			Files.writeString(skins.boxesFolder().resolve(name), json5, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** Writes the server-wide settings file, {@code config/metacraft-skins.json5}. */
	SkinFixtures settings(String json5) {
		try {
			Files.createDirectories(root.resolve("config"));
			Files.writeString(root.resolve("config/metacraft-skins.json5"), json5, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** {@link #skin(String, String, String, String)} named "<id> skin", with no description. */
	SkinFixtures skin(String id, String kind) {
		return skin(id, kind, id + " skin", null);
	}

	/** A Blockbench-style model with one texture called {@code texture}, a particle and all display slots. */
	static String model(String texture) {
		return """
				{ "format_version": "1.21.6", "credit": "Made with Blockbench", "texture_size": [32, 32],
				"textures": { "0": "%s", "particle": "%s" },
				"elements": [ { "from": [7, 0, 7], "to": [9, 16, 9], "faces": { "north": { "uv": [0, 0, 2, 16], "texture": "#0" } } } ],
				"display": %s }""".formatted(texture, texture, ALL_DISPLAY);
	}

	static final String ALL_DISPLAY = """
			{ "thirdperson_righthand": { "scale": [1, 1, 1] }, "thirdperson_lefthand": { "scale": [1, 1, 1] },
			"firstperson_righthand": { "scale": [1, 1, 1] }, "firstperson_lefthand": { "scale": [1, 1, 1] },
			"ground": { "scale": [0.5, 0.5, 0.5] }, "gui": { "rotation": [30, 225, 0] }, "head": { "scale": [1, 1, 1] },
			"fixed": { "scale": [1, 1, 1] }, "on_shelf": { "scale": [1, 1, 1] } }""";

	/** {@link #tintedHat}'s model. */
	static final String TINTED_MODEL = """
			{ "textures": { "0": "felt", "1": "band", "particle": "felt" },
			"elements": [ { "from": [0, 8, 0], "to": [16, 12, 16], "faces": {
			"north": { "uv": [0, 0, 1, 1], "texture": "#0", "tintindex": 1 },
			"south": { "uv": [0, 0, 1, 1], "texture": "#0" },
			"east": { "uv": [0, 0, 1, 1], "texture": "#0", "tintindex": -1 },
			"up": { "uv": [0, 0, 1, 1], "texture": "#1" } } } ],
			"display": %s }""".formatted(ALL_DISPLAY);

	ReloadReport reload() {
		return skins.reload();
	}

	/** The same server root started again: a new Skins reading only what is on disk, with its skins loaded. */
	Skins restart() {
		return restart(root.resolve("world"));
	}

	/** The same server root started again on the world in {@code world}. */
	Skins restart(Path world) {
		Skins next = Skins.at(root, world);
		next.reload();
		return next;
	}

	/** A player's ownership file, {@code skins-data/players/<uuid>.json}. */
	Path playerFile(UUID player) {
		return root.resolve("skins-data/players").resolve(player + ".json");
	}

	/** Writes a player's ownership file, as a hand edit or a file copied from another server would. */
	SkinFixtures playerFile(UUID player, String json) {
		try {
			Files.createDirectories(playerFile(player).getParent());
			Files.writeString(playerFile(player), json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** A player file owning {@code skin} since 2026-01-01, applied in the world stamped {@code world}. */
	static String appliedIn(UUID player, String name, String skin, UUID world) {
		return """
				{ "uuid": "%s", "name": "%s",
				"skins": { "%s": { "claimed": "2026-01-01T12:00:00Z", "application": { "id": "%s", "world": "%s" } } } }
				""".formatted(player, name, skin, UUID.randomUUID(), world);
	}

	/** Joins a new player the way {@code makeMockServerPlayerInLevel} does, but with a name of its own. */
	static Listening join(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
				new GameProfile(UUID.randomUUID(), "skins" + (int) (Math.random() * 1_000_000)), false);
		Listening player = new Listening(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		player.channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	/** A player who really joined (in the player list, so commands find them by name) and keeps what they're told. */
	static final class Listening extends ServerPlayer {
		/** Everything they were told, chat and action bar alike. */
		final List<String> heard = new ArrayList<>();
		/** What they were told on the action bar. */
		final List<String> actionBar = new ArrayList<>();
		/** Their connection's far end: every packet sent to them waits here, unencoded. */
		EmbeddedChannel channel;

		private Listening(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation information) {
			super(server, level, profile, information);
		}

		/**
		 * Finishes joining as a client does: the world has loaded and the join teleport is
		 * confirmed (until then the server ignores clicks on blocks). Then stands them at {@code where}.
		 */
		void arrive(Vec3 where) {
			connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			// The join teleport's id isn't public; it's one of the first few, and a wrong one is ignored.
			for (int id = 0; id < 8; id++) connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id, getX(), getY(), getZ(), 0, 0));
			setPos(where);
		}

		/** The end of a client tick: Polymer lets one item use through per client tick. */
		void tickEnd() {
			connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
		}

		/**
		 * The packets of this type sent to them since the last call, oldest first, with bundles
		 * (an entity coming into view, say) unpacked, flushed or not; any others are dropped.
		 */
		<T> List<T> sent(Class<T> type) {
			List<T> found = new ArrayList<>();
			// The server holds back flushes while it handles a tick's packets; a real connection flushes at the tick's end.
			channel.flushOutbound();
			for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) unpack(message, type, found);
			return found;
		}

		/**
		 * From now on, packets to them go through a real packet encoder, as on a server, so the mod's
		 * packet mixin and the drawing check run as they do in play. Read them with {@link #received}.
		 */
		void encodeForReal() {
			channel.pipeline().addLast("encoder", new UnconfiguredPipelineHandler.Outbound());
			// Inside a ServerPlayer, a bare Connection is WaypointTransmitter's.
			channel.pipeline().get(net.minecraft.network.Connection.class).setupOutboundProtocol(protocol());
		}

		/**
		 * The packets of this type encoded for them since {@link #encodeForReal} or the last call, decoded as
		 * a vanilla client decodes them, oldest first, bundles unpacked; any others are dropped.
		 */
		<T> List<T> received(Class<T> type) {
			List<T> found = new ArrayList<>();
			channel.flushOutbound();
			var codec = protocol().codec();
			for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
				if (!(message instanceof ByteBuf bytes)) continue;   // sent before encodeForReal
				try {
					unpack(codec.decode(bytes), type, found);
				} finally {
					bytes.release();
				}
			}
			return found;
		}

		private ProtocolInfo<ClientGamePacketListener> protocol() {
			return GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(level().getServer().registryAccess()));
		}

		private static <T> void unpack(Object message, Class<T> type, List<T> found) {
			if (type.isInstance(message)) found.add(type.cast(message));
			if (message instanceof BundlePacket<?> bundle) for (Object part : bundle.subPackets()) unpack(part, type, found);
		}

		@Override
		public void sendSystemMessage(Component message, boolean overlay) {
			heard.add(message.getString());
			if (overlay) actionBar.add(message.getString());
			super.sendSystemMessage(message, overlay);
		}
	}

	static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static byte[] bytes(Path file) {
		try {
			return Files.readAllBytes(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	SkinsPack pack() {
		return skins.pack().orElseThrow(() -> fail("no pack has been built"));
	}

	void delete(String id) {
		try (Stream<Path> files = Files.walk(folder(id))) {
			for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static Map<String, byte[]> unzip(byte[] zip) {
		Map<String, byte[]> files = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				files.put(entry.getName(), in.readAllBytes());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return files;
	}

	static String text(Map<String, byte[]> files, String path) {
		byte[] bytes = files.get(path);
		if (bytes == null) throw fail("the pack has no " + path + "; it has " + files.keySet());
		return new String(bytes, StandardCharsets.UTF_8);
	}

	static void assertThat(boolean condition, String message) {
		if (!condition) throw fail(message);
	}

	static GameTestAssertException fail(String message) {
		return new GameTestAssertException(Component.literal(message), 0);
	}
}
