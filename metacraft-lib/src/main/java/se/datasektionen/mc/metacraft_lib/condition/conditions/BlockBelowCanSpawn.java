package se.datasektionen.mc.metacraft_lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContextParameters;

import java.util.Set;

public class BlockBelowCanSpawn implements LootCondition {

	private static final BlockBelowCanSpawn INSTANCE = new BlockBelowCanSpawn();

	public static final MapCodec<BlockBelowCanSpawn> CODEC = MapCodec.unit(INSTANCE);

	public static BlockBelowCanSpawn getInstance() {
		return INSTANCE;
	}

	private BlockBelowCanSpawn() {}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.BLOCK_BELOW_CAN_SPAWN;
	}

	@Override
	public boolean test(LootContext ctx) {
		var pos = ctx.get(LootContextParameters.ORIGIN);
		if (pos == null) return false;
		return METAcraftContextParameters.getEntityType(ctx).map(
				entityType -> {
					BlockPos blockBelow = BlockPos.ofFloored(pos).down();
					return ctx.getWorld().getBlockState(blockBelow).allowsSpawning(ctx.getWorld(), blockBelow, entityType);
				}
		).orElse(false);
	}

	@Override
	public void validate(LootTableReporter reporter) {
		LootCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityType(reporter);
	}

	@Override
	public Set<LootContextParameter<?>> getRequiredParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}
}
