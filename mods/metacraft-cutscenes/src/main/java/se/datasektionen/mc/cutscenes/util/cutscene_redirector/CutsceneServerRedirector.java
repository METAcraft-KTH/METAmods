package se.datasektionen.mc.cutscenes.util.cutscene_redirector;

import com.mojang.datafixers.DataFixer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.resource.LifecycledResourceManager;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.util.ApiServices;
import net.minecraft.world.level.storage.LevelStorage;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;

import java.lang.reflect.*;
import java.util.List;

public class CutsceneServerRedirector {

	public static final String CUTSCENE_WORLD_FIELD_NAME = "metacraft_cutscenes$currentCutsceneWorld";
	public static final String REAL_SERVER_FIELD_NAME = "metacraft_cutscenes$realServer";

	private static final ApiServices API_SERVICES = new ApiServices(
			null, null,
			null, null
	);

	private static final LifecycledResourceManager PACK_MANAGER = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, List.of());

	private static final Class<? extends MinecraftServer> SERVER_TYPE = createBuilder().make().load(
			MinecraftServer.class.getClassLoader()
	).getLoaded();

	public static SaveLoader createSaveLoader() { //Public so it can be invoked by the default constructor of the proxy server.
		var registries = DummyDynamicRegistryContainers.createDynamicRegistries();
		var packs = DummyDynamicRegistryContainers.createDataPackContents(registries);
		return new SaveLoader(
				PACK_MANAGER, //Used by Fabric API
				packs, registries,
				DummySaveProperties.getInstance()
		);
	}

	private static DynamicType.Builder<? extends MinecraftServer> createBuilder() {
		try {
			var superConstructor = MinecraftServer.class.getConstructor(
					Thread.class, LevelStorage.Session.class, ResourcePackManager.class,
					SaveLoader.class, java.net.Proxy.class, DataFixer.class, ApiServices.class,
					WorldGenerationProgressListenerFactory.class
			);
			return new ByteBuddy()
					.subclass(
							MinecraftServer.class
					).implement(ExtraServerData.class)
					.defineField(CUTSCENE_WORLD_FIELD_NAME, CutsceneWorld.class, Visibility.PRIVATE)
					.defineField(REAL_SERVER_FIELD_NAME, MinecraftServer.class, Visibility.PRIVATE)
					.defineConstructor(Visibility.PUBLIC).intercept(
						//We want to invoke the constructor with as much null as possible without causing a null pointer exception.
						MethodCall.invoke(superConstructor).onSuper().with(
							null,
							DummySession.getInstance(),
							null
						).withMethodCall(//Must be a new one every time since Fabric mutates the dynamic registries.
								MethodCall.invoke(CutsceneServerRedirector.class.getDeclaredMethod("createSaveLoader"))
						).with(
								null, null,
								API_SERVICES,
								null
						)
					)
					.method(
							ElementMatchers.any()
					).intercept(
							MethodDelegation.to(CutsceneServerRedirector.Proxy.class)
					);
		} catch (NoSuchMethodException e) {
			Cutscenes.LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	private static void setPrivate(Object o, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
		var field = o.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(o, value);
		field.setAccessible(false);
	}

	public static MinecraftServer createProxyServer(MinecraftServer server, CutsceneWorld world) {
		try {
			Constructor<?> constructor = SERVER_TYPE.getDeclaredConstructor();

			MinecraftServer newServer = (MinecraftServer) constructor.newInstance();

			setPrivate(newServer, CUTSCENE_WORLD_FIELD_NAME, world);
			setPrivate(newServer, REAL_SERVER_FIELD_NAME, server);

			return newServer;

		} catch (
				NoClassDefFoundError | InstantiationException | NoSuchFieldException |
				IllegalAccessException | InvocationTargetException | NoSuchMethodException e
		) {
			Cutscenes.LOGGER.error(e.getMessage(), e);
			return server;
		}
	}

	private static <T> T getPrivate(Object object, String name) {
		try {
			Field field = object.getClass().getDeclaredField(name);
			field.setAccessible(true);
			var result = (T) field.get(object);
			field.setAccessible(false);
			return result;
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Cutscenes.LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	@SuppressWarnings("unused")
	public static class Proxy {
		public static CutsceneWorld metacraft_cutscenes$getCutsceneWorld(@This MinecraftServer server) {
			return getPrivate(server, CUTSCENE_WORLD_FIELD_NAME);
		}

		public static MinecraftServer metacraft_cutscenes$getRealServer(@This MinecraftServer server) {
			return getPrivate(server, REAL_SERVER_FIELD_NAME);
		}

		public static ServerScoreboard getScoreboard(@This ExtraServerData server) {
			return server.metacraft_cutscenes$getCutsceneWorld().getScoreboard();
		}

		@RuntimeType
		public static <T extends MinecraftServer & ExtraServerData> Object allOthers(
				@This T server, @Origin Method srcMethod,
				@AllArguments Object[] args,
				@SuperMethod(nullIfImpossible = true) Method superMethod
		) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
			if (server.metacraft_cutscenes$getRealServer() == null) { //Handle functions running inside the constructor itself.
				return superMethod.invoke(server, args);
			}
			var method = server.metacraft_cutscenes$getRealServer().getClass().getMethod(srcMethod.getName());
			return method.invoke(server.metacraft_cutscenes$getRealServer(), args);
		}
	}

	public interface ExtraServerData {
		CutsceneWorld metacraft_cutscenes$getCutsceneWorld();
		MinecraftServer metacraft_cutscenes$getRealServer();
	}

}
