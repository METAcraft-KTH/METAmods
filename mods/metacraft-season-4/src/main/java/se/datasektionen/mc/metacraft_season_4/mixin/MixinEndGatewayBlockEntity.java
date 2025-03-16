package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.EndGatewayFeatureConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_season_4.end.EndData;

@Mixin(EndGatewayBlockEntity.class)
public class MixinEndGatewayBlockEntity {

	@WrapWithCondition(
		method = "getOrCreateExitPortalPos",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/block/entity/EndGatewayBlockEntity;createPortal(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/gen/feature/EndGatewayFeatureConfig;)V"
		)
	)
	public boolean getOrCreateExitPortalPos(ServerWorld world, BlockPos pos, EndGatewayFeatureConfig config) {
		var poolElements = world.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
		var gateway = poolElements.get(EndData.END_GATEWAY_RETURN);
		if (gateway == null) {
			return true;
		} else {
			var rot = BlockRotation.values()[world.getRandom().nextInt(BlockRotation.values().length)];
			var element = gateway.getRandomElement(world.getRandom());
			var calcBox = element.getBoundingBox(world.getStructureTemplateManager(), BlockPos.ORIGIN, rot);
			var actualPos = pos.subtract(calcBox.getCenter());
			if (
					element.generate(
							world.getStructureTemplateManager(), world, world.getStructureAccessor(),
							world.getChunkManager().getChunkGenerator(), actualPos, actualPos,
							rot, BlockBox.infinite(), world.getRandom(),
							StructureLiquidSettings.APPLY_WATERLOGGING, false
					)
			) {
				var box = element.getBoundingBox(world.getStructureTemplateManager(), actualPos, rot);
				BlockPos.stream(box).forEach(blockPos -> {
					var e = world.getBlockEntity(blockPos);
					if (e instanceof EndGatewayBlockEntity g) {
						g.setExitPortalPos(config.getExitPos().orElse(null), config.isExact());
					}
				});
			}
			return false;
		}
	}

}
