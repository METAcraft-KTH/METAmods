package nu.metacraft.season_4.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.item.EnderEyeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import nu.metacraft.season_4.end.EndCommandActivation;

@Mixin(EnderEyeItem.class)
public class MixinEnderEyeItem {

	@WrapWithCondition(
		method = "useOnBlock",
		slice = @Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/block/pattern/BlockPattern$Result;getFrontTopLeft()Lnet/minecraft/util/math/BlockPos;"
			)
		),
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"
		)
	)
	public boolean allowPortal(World world, BlockPos pos, BlockState state, int flags) {
		return EndCommandActivation.getInstance(world.getServer()).getCommand().isEmpty();
	}

	@ModifyArg(
		method = "useOnBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;syncGlobalEvent(ILnet/minecraft/util/math/BlockPos;I)V"
		),
		index = 1
	)
	public BlockPos runCommandInstead(BlockPos pos, @Local(argsOnly = true) ItemUsageContext context) {
		EndCommandActivation.getInstance(context.getWorld().getServer()).getCommand().ifPresent(command -> {
			var source = new ServerCommandSource(
					CommandOutput.DUMMY, Vec3d.ofCenter(pos),
					Vec2f.ZERO, (ServerWorld) context.getWorld(), 2,
					"EndPortal", Text.of("EndPortal"), context.getWorld().getServer(),
					context.getPlayer()
			);
			context.getWorld().getServer().getCommandManager().parseAndExecute(source, command);
		});
		return pos;
	}

}
