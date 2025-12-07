package nu.metacraft.core.environment_attributes;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jetbrains.annotations.NotNull;

public interface DoubleModifier<Argument> extends AttributeModifier<@NotNull Double, @NotNull Argument> {

	DoubleModifier<@NotNull Double> ADD = (Simple) Double::sum;
	DoubleModifier<@NotNull Double> SUBTRACT = (Simple)(lhs, rhs) -> lhs - rhs;
	DoubleModifier<@NotNull Double> MULTIPLY = (Simple)(lhs, rhs) -> lhs * rhs;
	DoubleModifier<@NotNull Double> MINIMUM = (Simple) Math::min;
	DoubleModifier<@NotNull Double> MAXIMUM = (Simple) Math::max;

	interface Simple extends DoubleModifier<@NotNull Double> {
		@Override
		default @NotNull Codec<@NotNull Double> argumentCodec(@NotNull EnvironmentAttribute<@NotNull Double> environmentAttribute) {
			return Codec.DOUBLE;
		}

		@Override
		default @NotNull LerpFunction<@NotNull Double> argumentKeyframeLerp(@NotNull EnvironmentAttribute<@NotNull Double> environmentAttribute) {
			return Mth::lerp;
		}
	}

}
