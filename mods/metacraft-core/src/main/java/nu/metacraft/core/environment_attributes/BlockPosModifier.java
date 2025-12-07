package nu.metacraft.core.environment_attributes;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import nu.metacraft.core.util.helper.BlockPosHelper;
import org.jetbrains.annotations.NotNull;

public interface BlockPosModifier<Argument> extends AttributeModifier<@NotNull BlockPos, @NotNull Argument> {

	BlockPosModifier<@NotNull BlockPos> ADD = (Simple) BlockPos::offset;
	BlockPosModifier<@NotNull BlockPos> SUBTRACT = (Simple) BlockPos::subtract;

	interface Simple extends BlockPosModifier<@NotNull BlockPos> {
		@Override
		default @NotNull Codec<@NotNull BlockPos> argumentCodec(@NotNull EnvironmentAttribute<@NotNull BlockPos> environmentAttribute) {
			return BlockPos.CODEC;
		}

		@Override
		default @NotNull LerpFunction<@NotNull BlockPos> argumentKeyframeLerp(@NotNull EnvironmentAttribute<@NotNull BlockPos> environmentAttribute) {
			return BlockPosHelper::lerp;
		}
	}

}
