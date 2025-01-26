package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.ModInitializer;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.StructureTestUtil;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestFunction;
import net.minecraft.test.TestServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class TestHelper {

	public static ServerPlayerEntity addMockPlayer(TestContext ctx) {
		return addMockPlayer(ctx, "test-player", UUID.randomUUID());
	}

	public static ServerPlayerEntity addMockPlayer(
			TestContext ctx, String name, UUID uuid
	) {
		ConnectedClientData connectedClientData = ConnectedClientData.createDefault(new GameProfile(uuid, name), false);
		ServerPlayerEntity serverPlayerEntity = new ServerPlayerEntity(
				ctx.getWorld().getServer(), ctx.getWorld(), connectedClientData.gameProfile(), connectedClientData.syncedOptions()
		) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return true;
			}
		};
		ClientConnection clientConnection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(clientConnection);
		ctx.getWorld().getServer().getPlayerManager().onPlayerConnect(clientConnection, serverPlayerEntity, connectedClientData);
		return serverPlayerEntity;
	}

	public static void runTestServer(List<TestFunction> tests) throws IOException, SymlinkValidationException, InterruptedException {
		var storage = LevelStorage.create(Path.of("./run"));
		var session = storage.createSession("Test");
		var manager = VanillaDataPackProvider.createManager(session);
		var server = MinecraftServer.startServer(
				thread -> TestServer.create(
						thread, session, manager,
						tests, BlockPos.ORIGIN
				)
		);
		server.getThread().join();
		assert ((TestServerExtension) server).metacraft$testPassed();
	}

	@SafeVarargs
	public static void init(Supplier<? extends ModInitializer>... modsToLoad) {
		StructureTestUtil.testStructuresDirectoryName = Path.of("./src/test/resources/structures").toString();
		SharedConstants.isDevelopment = true;
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		for (Supplier<? extends ModInitializer> mod : modsToLoad) {
			mod.get().onInitialize();
		}
		Registries.bootstrap();
	}

	public interface TestServerExtension {
		boolean metacraft$testPassed();
	}

}
