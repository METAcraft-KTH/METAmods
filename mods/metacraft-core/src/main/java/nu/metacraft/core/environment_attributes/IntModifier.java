package nu.metacraft.core.environment_attributes;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jetbrains.annotations.NotNull;

public interface IntModifier<Argument> extends AttributeModifier<@NotNull Integer, @NotNull Argument> {

	IntModifier<@NotNull Integer> ADD = (Simple) Integer::sum;
	IntModifier<@NotNull Integer> SUBTRACT = (Simple)(lhs, rhs) -> lhs - rhs;
	IntModifier<@NotNull Integer> MULTIPLY = (Simple)(lhs, rhs) -> lhs * rhs;
	IntModifier<@NotNull Integer> MINIMUM = (Simple) Math::min;
	IntModifier<@NotNull Integer> MAXIMUM = (Simple) Math::max;

	interface Simple extends IntModifier<@NotNull Integer> {
		@Override
		default @NotNull Codec<@NotNull Integer> argumentCodec(@NotNull EnvironmentAttribute<@NotNull Integer> environmentAttribute) {
			return Codec.INT;
		}

		@Override
		default @NotNull LerpFunction<@NotNull Integer> argumentKeyframeLerp(@NotNull EnvironmentAttribute<@NotNull Integer> environmentAttribute) {
			return Mth::lerpInt;
		}
	}

}
