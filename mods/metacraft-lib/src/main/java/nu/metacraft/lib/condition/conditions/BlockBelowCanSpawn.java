package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class BlockBelowCanSpawn implements LootItemCondition {

	private static final BlockBelowCanSpawn INSTANCE = new BlockBelowCanSpawn();

	public static final MapCodec<BlockBelowCanSpawn> CODEC = MapCodec.unit(INSTANCE);

	public static BlockBelowCanSpawn getInstance() {
		return INSTANCE;
	}

	private BlockBelowCanSpawn() {}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.BLOCK_BELOW_CAN_SPAWN;
	}

	@Override
	public boolean test(LootContext ctx) {
		var pos = ctx.getOptionalParameter(LootContextParams.ORIGIN);
		if (pos == null) return false;
		return METAcraftContextParameters.getEntityType(ctx).map(
				entityType -> {
					BlockPos blockBelow = BlockPos.containing(pos).below();
					return ctx.getLevel().getBlockState(blockBelow).isValidSpawn(ctx.getLevel(), blockBelow, entityType);
				}
		).orElse(false);
	}

	@Override
	public void validate(ValidationContext reporter) {
		LootItemCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityType(reporter);
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}
}
