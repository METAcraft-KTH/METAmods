package nu.metacraft.core.entity.ai;

import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.entity.ai.sensors.LastKnownOxygen;

import java.util.function.Supplier;

public class METAcraftSensorTypes {

	public static final SensorType<LastKnownOxygen> LAST_KNOWN_OXYGEN = register("last_known_oxygen", LastKnownOxygen::new);

	public static void init() {

	}

	private static <U extends Sensor<?>> SensorType<U> register(String id, Supplier<U> factory) {
		return Registry.register(Registries.SENSOR_TYPE, METAcraftCore.getID(id), new SensorType<U>(factory));
	}

}
