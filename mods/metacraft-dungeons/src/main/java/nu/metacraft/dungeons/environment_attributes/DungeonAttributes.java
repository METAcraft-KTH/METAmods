package nu.metacraft.dungeons.environment_attributes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.level.Level;
import nu.metacraft.core.environment_attributes.METAcraftAttributeTypes;
import nu.metacraft.core.util.TeleportPredicate;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.lib.time_getter.RegularTimeGetter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class DungeonAttributes {

	public static final EnvironmentAttribute<@NotNull Boolean> DUNGEON = register(
			"dungeons/is_dungeon_dimension", EnvironmentAttribute.builder(AttributeTypes.BOOLEAN).defaultValue(false).notPositional()
	);

	public static final EnvironmentAttribute<@NotNull Integer> DUNGEON_WIDTH = register(
			"dungeons/dungeon_width", EnvironmentAttribute.builder(METAcraftAttributeTypes.INTEGER).defaultValue(256).notPositional()
	);

	public static final EnvironmentAttribute<@NotNull Double> MAX_RANGE_FROM_EXIT = register(
			"dungeons/max_range_from_exit", EnvironmentAttribute.builder(METAcraftAttributeTypes.DOUBLE).defaultValue(100.0).spatiallyInterpolated()
	);

	public static final EnvironmentAttribute<@NotNull BlockPos> EXIT_POS = register(
			"dungeons/exit_pos", EnvironmentAttribute.builder(METAcraftAttributeTypes.BLOCK_POS).defaultValue(BlockPos.ZERO).spatiallyInterpolated()
	);

	public static final EnvironmentAttribute<@NotNull ResourceKey<@NotNull Level>> EXIT_DIM = register(
			"dungeons/exit_dim", EnvironmentAttribute.builder(METAcraftAttributeTypes.DIMENSION).defaultValue(Level.OVERWORLD)
	);

	public static final EnvironmentAttribute<@NotNull List<@NotNull TeleportPredicate>> SHOULD_TELEPORT = register(
			"dungeons/should_teleport", EnvironmentAttribute.builder(DungeonAttributeTypes.TELEPORT_PREDICATES).defaultValue(List.of())
	);

	public static final EnvironmentAttribute<@NotNull Optional<RegularTimeGetter>> RESET_CHECKER = register(
			"dungeons/reset_checker", EnvironmentAttribute.builder(METAcraftAttributeTypes.REGULAR_TIME_GETTER).defaultValue(Optional.empty()).notPositional()
	);





	public static void init() {

	}

	private static <Value> EnvironmentAttribute<@NotNull Value> register(String string, EnvironmentAttribute.Builder<@NotNull Value> builder) {
		EnvironmentAttribute<@NotNull Value> environmentAttribute = builder.build();
		Registry.register(BuiltInRegistries.ENVIRONMENT_ATTRIBUTE, METAcraftDungeons.getID(string), environmentAttribute);
		return environmentAttribute;
	}

}
