package nu.metacraft.season_4.entity.ai;

import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.season_4.Season4;
import nu.metacraft.season_4.entity.ai.sensors.TargetEntitySensor;

import java.util.function.Supplier;

public class Season4Sensors {

	public static final SensorType<TargetEntitySensor> TARGET_ENTITY_SENSOR = register(
			"target_entity_sensor", TargetEntitySensor::new
	);

	public static void init() {

	}

	private static <U extends Sensor<?>> SensorType<U> register(String id, Supplier<U> factory) {
		return Registry.register(Registries.SENSOR_TYPE, Season4.getID(id), new SensorType<>(factory));
	}

}
