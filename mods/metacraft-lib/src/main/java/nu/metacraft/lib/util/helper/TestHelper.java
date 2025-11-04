package nu.metacraft.lib.util.helper;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;
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

	public static ServerPlayer addMockPlayer(GameTestHelper ctx) {
		return addMockPlayer(ctx, "test-player", UUID.randomUUID());
	}

	public static ServerPlayer addMockPlayer(
			GameTestHelper ctx, String name, UUID uuid
	) {
		CommonListenerCookie connectedClientData = CommonListenerCookie.createInitial(new GameProfile(uuid, name), false);
		Connection clientConnection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(clientConnection);

		var prepareSpawnTask = new PrepareSpawnTask(ctx.getLevel().getServer(), new NameAndId(uuid, name));
		prepareSpawnTask.start(p -> {}); //Initialize spawn point preparation and player data loading task.
		ctx.getLevel().getServer().managedBlock(prepareSpawnTask::tick); //Wait for server to find a spawn point.
		return prepareSpawnTask.spawnPlayer(clientConnection, connectedClientData); //Finish loading the player.
	}

	public static void runTestServer(
			String testNamespace, String testsPath
	) throws IOException, ContentValidationException, InterruptedException {
		var path = Path.of("./run/Test");
		var file = path.toFile();
		if (file.isDirectory()) {
			Files.deleteRecursively(file);
		}
		var storage = LevelStorageSource.createDefault(path.getParent());
		var session = storage.validateAndCreateAccess(file.getName());
		var manager = new PackRepository(
				new ServerPacksSource(session.parent().getWorldDirValidator()),
				new FolderRepositorySource(
						session.getLevelPath(LevelResource.DATAPACK_DIR),
						PackType.SERVER_DATA, PackSource.WORLD,
						session.parent().getWorldDirValidator()
				),
				new RepositorySource() {
					@Override
					public void loadPacks(Consumer<Pack> profileAdder) {
						var path = Path.of("./src/test/resources");
						if (path.resolve("data").toFile().exists()) {
							profileAdder.accept(
									new Pack(
											new PackLocationInfo(
													"metacraft:test_container", Component.literal("Test Pack"),
													PackSource.BUILT_IN, Optional.empty()
											),
											new PathPackResources.PathResourcesSupplier(path),
											new Pack.Metadata(
													Component.literal("Test Pack"),
													PackCompatibility.COMPATIBLE,
													FeatureFlagSet.of(),
													List.of()
											),
											new PackSelectionConfig(true, Pack.Position.BOTTOM, true)
									)
							);
						}
					}
				}
		);
		var server = MinecraftServer.spin(thread -> GameTestServer.create(
				thread, session, manager,
				Optional.of(testNamespace + ":" + testsPath), false
		));
		server.getRunningThread().join();
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
		StructureUtils.testStructuresDir = Path.of("./src/test/resources/structures");
		SharedConstants.IS_RUNNING_IN_IDE = true;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		for (Supplier<? extends ModInitializer> mod : modsToLoad) {
			var modInstance = mod.get();
			if (ALREADY_LOADED.contains(modInstance.getClass())) continue;
			modInstance.onInitialize();
			ALREADY_LOADED.add(modInstance.getClass());
		}
		additionalRegistrations.run();
		BuiltInRegistries.bootStrap();
	}

	public interface TestServerExtension {
		boolean metacraft$testPassed();
	}

}
