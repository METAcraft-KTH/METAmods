package se.datasektionen.mc.cutscenes.util.cutscene_redirector;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.metacraft_lib.util.IntermediaryNames;

import java.lang.reflect.*;

public class CutsceneServerRedirector {

	public static final String CUTSCENE_WORLD_FIELD_NAME = "metacraft_cutscenes$currentCutsceneWorld";
	public static final String REAL_SERVER_FIELD_NAME = "metacraft_cutscenes$realServer";

	private static final Class<? extends MinecraftServer> SERVER_TYPE = createBuilder().make().load(
			MinecraftServer.class.getClassLoader()
	).getLoaded();

	private static final Objenesis OBJENESIS = new ObjenesisStd();
	private static final ObjectInstantiator<? extends MinecraftServer> SERVER_INSTANTIATOR = OBJENESIS.getInstantiatorOf(SERVER_TYPE);

	private static DynamicType.Builder<? extends MinecraftServer> createBuilder() {
		return new ByteBuddy()
				.subclass(
						MinecraftServer.class
				).implement(ExtraServerData.class)
				.defineField(CUTSCENE_WORLD_FIELD_NAME, CutsceneWorld.class, Visibility.PRIVATE)
				.defineField(REAL_SERVER_FIELD_NAME, MinecraftServer.class, Visibility.PRIVATE)
				.method(
						ElementMatchers.any()
				).intercept(
						MethodDelegation.to(CutsceneServerRedirector.Proxy.class)
				);
	}

	private static void setPrivate(Object o, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
		var field = o.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(o, value);
		field.setAccessible(false);
	}

	public static MinecraftServer createProxyServer(MinecraftServer server, CutsceneWorld world) {
		try {
			MinecraftServer newServer = SERVER_INSTANTIATOR.newInstance();

			setPrivate(newServer, CUTSCENE_WORLD_FIELD_NAME, world);
			setPrivate(newServer, REAL_SERVER_FIELD_NAME, server);

			return newServer;

		} catch (
				NoClassDefFoundError | NoSuchFieldException | IllegalAccessException e
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
			if (srcMethod.getName().equals(IntermediaryNames.SERVER_GET_SCOREBOARD)) {
				return getScoreboard(server);
			}
			var method = server.metacraft_cutscenes$getRealServer().getClass().getMethod(srcMethod.getName());
			return method.invoke(server.metacraft_cutscenes$getRealServer(), args);
		}
	}

	public interface ExtraServerData {
		CutsceneWorld metacraft_cutscenes$getCutsceneWorld();
		MinecraftServer metacraft_cutscenes$getRealServer();
	}

	public static void init() {
		//Classload this class to fix lag spike when cutscene played for the first time after startup.
	}

}
