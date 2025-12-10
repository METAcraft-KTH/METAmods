package nu.metacraft.core.entity.ai;

import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.entity.ai.sensors.LastKnownOxygen;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class METAcraftSensorTypes {

	public static final SensorType<LastKnownOxygen> LAST_KNOWN_OXYGEN = register("last_known_oxygen", LastKnownOxygen::new);

	public static void init() {

	}

	private static <U extends Sensor<?>> SensorType<U> register(String id, Supplier<U> factory) {
		return Registry.register(BuiltInRegistries.SENSOR_TYPE, METAcraftCore.getID(id), new SensorType<U>(factory));
	}

}
