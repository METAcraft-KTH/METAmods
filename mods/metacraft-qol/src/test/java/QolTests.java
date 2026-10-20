import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.qol.Qol;
import nu.metacraft.qol.QolConfig;
import nu.metacraft.qol.concrete_cauldron.ConcreteCauldronConfig;
import nu.metacraft.qol.silence_mobs.SilenceMobsConfig;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;
import net.minecraft.nbt.NbtOps;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Every feature's game tests, run together on one test server (registries freeze after the first init). */
public class QolTests {

	private static boolean initialised = false;

	/** Every test class shares one JVM, so all of them go through here. */
	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TestHelper.init(
				() -> {
					registerTestCommands();
					VoidAnchorTests.registerTests();
					ConcreteCauldronTests.registerTests();
					SilenceMobsTests.registerTests();
					register("config/dialog_round_trips", QolTests::dialogRoundTrips);
				},
				// ConfigGUI too: its player preferences registry must exist before the first (mock) player does
				METAcraftLib::new, METAcraftCore::new, ConfigGUI::new, Qol::new
		);
	}

	static void register(String path, Consumer<GameTestHelper> test) {
		Registry.register(BuiltInRegistries.TEST_FUNCTION, Qol.getID(path), test);
	}

	/**
	 * {@code qoltest <feature> <true|false>}: switches a feature on or off through the real config
	 * file and a reload. Each feature's "off" tests run in their own environment (a batch of their
	 * own) whose setup and teardown functions call this, so no other test sees the feature off.
	 */
	static void registerTestCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("qoltest")
						.then(toggle("void_anchor", (config, on) -> {
							var v = config.voidAnchor();
							return new QolConfig(
									new VoidAnchorConfig(on, v.fuelItem(), v.triggerYOffset(), v.riftDepth(), v.descentSpeed(), v.riftTicks(), v.riftSize(), v.riftStyle()),
									config.concreteCauldron(), config.silenceMobs()
							);
						}))
						.then(toggle("concrete_cauldron", (config, on) -> new QolConfig(config.voidAnchor(), new ConcreteCauldronConfig(on), config.silenceMobs())))
						.then(toggle("silence_mobs", (config, on) -> new QolConfig(config.voidAnchor(), config.concreteCauldron(), new SilenceMobsConfig(on))))
		));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String feature, BiFunction<QolConfig, Boolean, QolConfig> withEnabled) {
		return Commands.literal(feature).then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
			var changed = withEnabled.apply(QolConfig.getInstance(), BoolArgumentType.getBool(ctx, "enabled"));
			var json = QolConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, changed).getOrThrow();
			try {
				Files.writeString(QolConfig.PATH, json.toString());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			QolConfig.reload();
			return 1;
		}));
	}

	/**
	 * Every packet the server has sent {@code player}, a mock player, since the last call. A mock
	 * player's connection ends in an EmbeddedChannel with no encoder, so what the server sends piles
	 * up there as packet objects.
	 */
	static List<Object> sentTo(ServerPlayer player) {
		try {
			var listenerConnection = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			listenerConnection.setAccessible(true);
			var connectionChannel = Connection.class.getDeclaredField("channel");
			connectionChannel.setAccessible(true);
			var channel = (EmbeddedChannel) connectionChannel.get(listenerConnection.get(player.connection));
			channel.flushOutbound();
			var sent = new ArrayList<Object>();
			for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
				sent.add(message);
			}
			return sent;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't read a mock player's packets", e);
		}
	}

	/** The actionbar messages among {@link #sentTo}. */
	static List<Component> overlaysSentTo(ServerPlayer player) {
		var overlays = new ArrayList<Component>();
		for (var packet : sentTo(player)) {
			if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
				overlays.add(chat.content());
			}
		}
		return overlays;
	}

	/**
	 * metacraft-config-gui's dialog ({@code /meta-config-screen metacraft-qol}) reads the codec the
	 * way it would to show and save the config: a changed config goes through it and comes back.
	 */
	static void dialogRoundTrips(GameTestHelper ctx) {
		var lookup = ctx.getLevel().registryAccess();
		var type = CodecDialog.Type.from(CodecParser.parse(QolConfig.CODEC.codec(), lookup));
		var v = VoidAnchorConfig.DEFAULT;
		var changed = new QolConfig(
				new VoidAnchorConfig(v.enabled(), v.fuelItem(), 5, v.riftDepth(), v.descentSpeed(), v.riftTicks(), 2.5f, RiftStyle.SHATTER),
				new ConcreteCauldronConfig(false), SilenceMobsConfig.DEFAULT
		);
		var tag = type.encode(NbtOps.INSTANCE, changed).getOrThrow();
		var back = type.parse(lookup, tag).getOrThrow();
		ctx.assertTrue(changed.equals(back), "the dialog read back " + back);
		ctx.succeed();
	}

	@Test
	public void gameTests() throws Exception {
		TestHelper.runTestServer("metacraft", "*");
	}

}
