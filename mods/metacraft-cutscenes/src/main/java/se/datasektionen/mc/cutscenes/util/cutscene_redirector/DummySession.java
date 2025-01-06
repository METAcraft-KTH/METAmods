package se.datasektionen.mc.cutscenes.util.cutscene_redirector;

import net.minecraft.world.level.storage.LevelStorage;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public class DummySession {

	private static final LevelStorage.Session DUMMY_SESSION = createDummySession();

	public static LevelStorage.Session getInstance() {
		return DUMMY_SESSION;
	}

	private static LevelStorage.Session createDummySession() {
		try {
			Constructor<LevelStorage.Session> constructor = LevelStorage.Session.class.getDeclaredConstructor(
					LevelStorage.class, String.class, Path.class
			); //The first LevelStorage argument is required because Session is a non-static inner class of LevelStorage.
			return constructor.newInstance(null, null, Path.of("."));
		} catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
