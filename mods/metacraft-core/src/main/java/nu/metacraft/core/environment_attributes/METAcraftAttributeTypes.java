package nu.metacraft.core.environment_attributes;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.AttributeType;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.Level;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.util.helper.BlockPosHelper;
import nu.metacraft.lib.time_getter.RegularTimeGetter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public class METAcraftAttributeTypes {

	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull Integer, ?>> INT_LIBRARY = Map.of(
			AttributeModifier.OperationId.ADD,
			IntModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			IntModifier.SUBTRACT,
			AttributeModifier.OperationId.MULTIPLY,
			IntModifier.MULTIPLY,
			AttributeModifier.OperationId.MINIMUM,
			IntModifier.MINIMUM,
			AttributeModifier.OperationId.MAXIMUM,
			IntModifier.MAXIMUM
	);


	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull Double, ?>> DOUBLE_LIBRARY = Map.of(
			AttributeModifier.OperationId.ADD,
			DoubleModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			DoubleModifier.SUBTRACT,
			AttributeModifier.OperationId.MULTIPLY,
			DoubleModifier.MULTIPLY,
			AttributeModifier.OperationId.MINIMUM,
			DoubleModifier.MINIMUM,
			AttributeModifier.OperationId.MAXIMUM,
			DoubleModifier.MAXIMUM
	);

	private static final Map<AttributeModifier.OperationId, AttributeModifier<@NotNull BlockPos, ?>> BLOCK_POS_LIBRARY = Map.of(
			AttributeModifier.OperationId.ADD,
			BlockPosModifier.ADD,
			AttributeModifier.OperationId.SUBTRACT,
			BlockPosModifier.SUBTRACT
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
					BlockPos.CODEC, BLOCK_POS_LIBRARY, BlockPosHelper::lerp
			)
	);

	public static final AttributeType<@NotNull ResourceKey<@NotNull Level>> DIMENSION = register(
			"dimension", AttributeType.ofNotInterpolated(Level.RESOURCE_KEY_CODEC)
	);

	public static final AttributeType<@NotNull Optional<@NotNull RegularTimeGetter>> REGULAR_TIME_GETTER = register(
			"regular_time_getter", AttributeType.ofNotInterpolated(makeOptCodec(RegularTimeGetter.REGISTRY_CODEC))
	);

	public static void init() {

	}

	private static <Value> AttributeType<@NotNull Value> register(String string, AttributeType<@NotNull Value> attributeType) {
		Registry.register(BuiltInRegistries.ATTRIBUTE_TYPE, METAcraftCore.getID(string), attributeType);
		return attributeType;
	}

}
