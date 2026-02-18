package nu.metacraft.bosses.item.components;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.util.valueproviders.IntProviders;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.util.DoubleTeamHandler;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;

public class BossComponents {

	public static final DataComponentType<WeightedList<EntityHelper.SpawnEntry>> SPAWNS = register(
			"spawn_pool", builder -> builder.persistent(EntityHelper.SpawnEntry.POOL_CODEC)
	);

	public static final DataComponentType<Double> MAX_RANGE = register(
			"max_range", builder -> builder.persistent(Codec.DOUBLE)
	);

	public static final DataComponentType<IntProvider> TRY_COUNT = register(
			"try_count", builder -> builder.persistent(IntProviders.NON_NEGATIVE_CODEC)
	);

	public static final DataComponentType<DoubleTeamHandler.Settings> DOUBLE_TEAM_SETTINGS = register(
			"double_team_settings", builder -> builder.persistent(DoubleTeamHandler.Settings.CODEC.codec())
	);

	public static void init() {

	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, METAcraftBosses.getID(id),
				builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
