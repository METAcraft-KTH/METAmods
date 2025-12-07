package nu.metacraft.dungeons.environment_attributes;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.AttributeType;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.Level;
import nu.metacraft.core.util.TeleportPredicate;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.lib.time_getter.RegularTimeGetter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DungeonAttributeTypes {

	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull Integer, ?>> INT_LIBRARY = Map.of(
			/*AttributeModifier.OperationId.ALPHA_BLEND,
			FloatModifier.ALPHA_BLEND,
			AttributeModifier.OperationId.ADD,
			FloatModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			FloatModifier.SUBTRACT,
			AttributeModifier.OperationId.MULTIPLY,
			FloatModifier.MULTIPLY,
			AttributeModifier.OperationId.MINIMUM,
			FloatModifier.MINIMUM,
			AttributeModifier.OperationId.MAXIMUM,
			FloatModifier.MAXIMUM*/
	);


	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull Double, ?>> DOUBLE_LIBRARY = Map.of(
			/*AttributeModifier.OperationId.ALPHA_BLEND,
			FloatModifier.ALPHA_BLEND,
			AttributeModifier.OperationId.ADD,
			FloatModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			FloatModifier.SUBTRACT,
			AttributeModifier.OperationId.MULTIPLY,
			FloatModifier.MULTIPLY,
			AttributeModifier.OperationId.MINIMUM,
			FloatModifier.MINIMUM,
			AttributeModifier.OperationId.MAXIMUM,
			FloatModifier.MAXIMUM*/
	);

	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull BlockPos, ?>> BLOCK_POS_LIBRARY = Map.of(
			/*AttributeModifier.OperationId.ALPHA_BLEND,
			FloatModifier.ALPHA_BLEND,
			AttributeModifier.OperationId.ADD,
			FloatModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			FloatModifier.SUBTRACT,
			AttributeModifier.OperationId.MULTIPLY,
			FloatModifier.MULTIPLY,
			AttributeModifier.OperationId.MINIMUM,
			FloatModifier.MINIMUM,
			AttributeModifier.OperationId.MAXIMUM,
			FloatModifier.MAXIMUM*/
	);

	private static <T> Codec<Optional<T>> makeOptCodec(Codec<T> codec) {
		return codec.xmap(Optional::of, Optional::get);
	}



	public static final AttributeType<@NotNull Integer> INTEGER = register(
			"integer", AttributeType.ofInterpolated(Codec.INT, INT_LIBRARY, Mth::lerpInt)
	);


	public static final AttributeType<@NotNull Double> DOUBLE = register(
			"double", AttributeType.ofInterpolated(Codec.DOUBLE, DOUBLE_LIBRARY, Mth::lerp)
	);

	public static final AttributeType<@NotNull BlockPos> BLOCK_POS = register(
			"block_pos", AttributeType.ofInterpolated(
					BlockPos.CODEC, BLOCK_POS_LIBRARY, (f, lhs, rhs) -> {
						if (f <= 0) return lhs;
						if (f >= 1) return rhs;
						return new BlockPos(
								Mth.lerpInt(f, lhs.getX(), rhs.getX()),
								Mth.lerpInt(f, lhs.getY(), rhs.getY()),
								Mth.lerpInt(f, lhs.getZ(), rhs.getZ())
						);
					}
			)
	);

	public static final AttributeType<@NotNull ResourceKey<@NotNull Level>> DIMENSION = register(
			"dimension", AttributeType.ofNotInterpolated(Level.RESOURCE_KEY_CODEC)
	);

	public static final AttributeType<@NotNull List<@NotNull TeleportPredicate>> TELEPORT_PREDICATES = register(
			"teleport_predicates", AttributeType.ofNotInterpolated(TeleportPredicate.CODEC.listOf())
	);

	public static final AttributeType<@NotNull Optional<@NotNull RegularTimeGetter>> REGULAR_TIME_GETTER = register(
			"regular_time_getter", AttributeType.ofNotInterpolated(makeOptCodec(RegularTimeGetter.REGISTRY_CODEC))
	);






	public static void init() {

	}

	private static <Value> AttributeType<@NotNull Value> register(String string, AttributeType<@NotNull Value> attributeType) {
		Registry.register(BuiltInRegistries.ATTRIBUTE_TYPE, METAcraftDungeons.getID(string), attributeType);
		return attributeType;
	}

}
