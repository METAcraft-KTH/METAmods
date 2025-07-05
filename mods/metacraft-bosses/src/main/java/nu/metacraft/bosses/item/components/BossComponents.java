package nu.metacraft.bosses.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.intprovider.IntProvider;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.util.DoubleTeamHandler;

import java.util.function.UnaryOperator;

public class BossComponents {

	public static final ComponentType<Pool<EntityHelper.SpawnEntry>> SPAWNS = register(
			"spawn_pool", builder -> builder.codec(EntityHelper.SpawnEntry.POOL_CODEC)
	);

	public static final ComponentType<Double> MAX_RANGE = register(
			"max_range", builder -> builder.codec(Codec.DOUBLE)
	);

	public static final ComponentType<IntProvider> TRY_COUNT = register(
			"try_count", builder -> builder.codec(IntProvider.NON_NEGATIVE_CODEC)
	);

	public static final ComponentType<DoubleTeamHandler.Settings> DOUBLE_TEAM_SETTINGS = register(
			"double_team_settings", builder -> builder.codec(DoubleTeamHandler.Settings.CODEC.codec())
	);

	public static void init() {

	}

	private static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, METAcraftBosses.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
