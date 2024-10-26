package se.datasektionen.mc.metacraft_lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;

import java.util.Set;

public class SolidBlockBelow implements LootCondition {

	private static final SolidBlockBelow INSTANCE = new SolidBlockBelow();

	public static final MapCodec<SolidBlockBelow> CODEC = MapCodec.unit(INSTANCE);

	public static SolidBlockBelow getInstance() {
		return INSTANCE;
	}

	private SolidBlockBelow() {}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.SOLID_BLOCK_BELOW;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var origin = lootContext.get(LootContextParameters.ORIGIN);
		if (origin == null) return false;
		var pos = BlockPos.ofFloored(origin).down();
		return !lootContext.getWorld().getBlockState(pos).getCollisionShape(lootContext.getWorld(), pos).isEmpty();
	}

	@Override
	public Set<ContextParameter<?>> getAllowedParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}
}
