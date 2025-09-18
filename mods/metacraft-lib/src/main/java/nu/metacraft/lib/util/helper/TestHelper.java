package nu.metacraft.lib.util.helper;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.ModInitializer;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.resource.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.PrepareSpawnTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestInstanceUtil;
import net.minecraft.test.TestServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.util.Files;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TestHelper {

	private static final Set<Class<? extends ModInitializer>> ALREADY_LOADED = new HashSet<>();

	private static boolean junit = false;

	static {
		for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
			if (element.getClassName().startsWith("org.junit.")) {
				junit = true;
				break;
			}
		}
	}

	public static boolean isJunit() {
		return junit;
	}

	public static ServerPlayerEntity addMockPlayer(TestContext ctx) {
		return addMockPlayer(ctx, "test-player", UUID.randomUUID());
	}

	public static ServerPlayerEntity addMockPlayer(
			TestContext ctx, String name, UUID uuid
	) {
		ConnectedClientData connectedClientData = ConnectedClientData.createDefault(new GameProfile(uuid, name), false);
		ClientConnection clientConnection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(clientConnection);

		var prepareSpawnTask = new PrepareSpawnTask(ctx.getWorld().getServer(), new PlayerConfigEntry(uuid, name));
		prepareSpawnTask.sendPacket(p -> {}); //Initialize spawn point preparation and player data loading task.
		ctx.getWorld().getServer().runTasks(prepareSpawnTask::hasFinished); //Wait for server to find a spawn point.
		return prepareSpawnTask.onReady(clientConnection, connectedClientData); //Finish loading the player.
	}

	public static void runTestServer(
			String testNamespace, String testsPath
	) throws IOException, SymlinkValidationException, InterruptedException {
		var path = Path.of("./run/Test");
		var file = path.toFile();
		if (file.isDirectory()) {
			Files.deleteRecursively(file);
		}
		var storage = LevelStorage.create(path.getParent());
		var session = storage.createSession(file.getName());
		var manager = new ResourcePackManager(
				new VanillaDataPackProvider(session.getLevelStorage().getSymlinkFinder()),
				new FileResourcePackProvider(
						session.getDirectory(WorldSavePath.DATAPACKS),
						ResourceType.SERVER_DATA, ResourcePackSource.WORLD,
						session.getLevelStorage().getSymlinkFinder()
				),
				new ResourcePackProvider() {
					@Override
					public void register(Consumer<ResourcePackProfile> profileAdder) {
						var path = Path.of("./src/test/resources");
						if (path.resolve("data").toFile().exists()) {
							profileAdder.accept(
									new ResourcePackProfile(
											new ResourcePackInfo(
													"metacraft:test_container", Text.literal("Test Pack"),
													ResourcePackSource.BUILTIN, Optional.empty()
											),
											new DirectoryResourcePack.DirectoryBackedFactory(path),
											new ResourcePackProfile.Metadata(
													Text.literal("Test Pack"),
													ResourcePackCompatibility.COMPATIBLE,
													FeatureSet.empty(),
													List.of()
											),
											new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.BOTTOM, true)
									)
							);
						}
					}
				}
		);
		var server = MinecraftServer.startServer(thread -> TestServer.create(
				thread, session, manager,
				Optional.of(testNamespace + ":" + testsPath), false
		));
		server.getThread().join();
		assert ((TestServerExtension) server).metacraft$testPassed();
	}

	@SafeVarargs
	public static void init(
			Supplier<? extends ModInitializer>... modsToLoad
	) {
		init(() -> {}, modsToLoad);
	}

	@SafeVarargs
	public static void init(
			Runnable additionalRegistrations,
			Supplier<? extends ModInitializer>... modsToLoad
	) {
		TestInstanceUtil.testStructuresDirectoryName = Path.of("./src/test/resources/structures");
		SharedConstants.isDevelopment = true;
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		for (Supplier<? extends ModInitializer> mod : modsToLoad) {
			var modInstance = mod.get();
			if (ALREADY_LOADED.contains(modInstance.getClass())) continue;
			modInstance.onInitialize();
			ALREADY_LOADED.add(modInstance.getClass());
		}
		additionalRegistrations.run();
		Registries.bootstrap();
	}

	public interface TestServerExtension {
		boolean metacraft$testPassed();
	}

}
