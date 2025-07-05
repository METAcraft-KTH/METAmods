package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;

import java.util.Set;

public class IsNearGround implements LootCondition {

	private static final IsNearGround INSTANCE = new IsNearGround();

	public static final MapCodec<IsNearGround> CODEC = MapCodec.unit(INSTANCE);

	public static IsNearGround getInstance() {
		return INSTANCE;
	}

	private IsNearGround() {}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.IS_NEAR_GROUND;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var cPos = lootContext.get(LootContextParameters.ORIGIN);
		if (cPos != null && lootContext.getWorld().getBottomY() + 15 > cPos.getY()) {
			return true;
		}
		var box = METAcraftContextParameters.getBoundingBox(lootContext).orElse(null);
		if (box == null) return false;
		BlockPos bottomCorner = BlockPos.ofFloored(
				box.minX-5,
				box.minY,
				box.minZ-5
		);
		BlockPos topCorner = BlockPos.ofFloored(
				box.maxX+5,
				box.minY,
				box.maxZ+5
		);
		for (var pos : BlockPos.iterate(bottomCorner, topCorner)) {
			int groundY = lootContext.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
			if (pos.getY() - groundY < 5) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void validate(LootTableReporter reporter) {
		LootCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityOrBlockEntity(reporter, METAcraftContextParameters.BOUNDING_BOX);
	}

	@Override
	public Set<ContextParameter<?>> getAllowedParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}
}
