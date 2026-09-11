package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.condition.METAcraftConditions;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SolidBlockBelow implements LootItemCondition {

	private static final SolidBlockBelow INSTANCE = new SolidBlockBelow();

	public static final MapCodec<SolidBlockBelow> CODEC = MapCodec.unit(INSTANCE);

	public static SolidBlockBelow getInstance() {
		return INSTANCE;
	}

	private SolidBlockBelow() {}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.SOLID_BLOCK_BELOW;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var origin = lootContext.getOptional(LootContextParams.ORIGIN);
		if (origin == null) return false;
		var pos = BlockPos.containing(origin).below();
		return !lootContext.getLevel().getBlockState(pos).getCollisionShape(lootContext.getLevel(), pos).isEmpty();
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}
}
