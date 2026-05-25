package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class IsNearGround implements LootItemCondition {

	private static final IsNearGround INSTANCE = new IsNearGround();

	public static final MapCodec<IsNearGround> CODEC = MapCodec.unit(INSTANCE);

	public static IsNearGround getInstance() {
		return INSTANCE;
	}

	private IsNearGround() {}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.IS_NEAR_GROUND;
	}

	@Override
	public boolean test(LootContext lootContext) {
		var cPos = lootContext.getOptionalParameter(LootContextParams.ORIGIN);
		if (cPos != null && lootContext.getLevel().getMinY() + 15 > cPos.y()) {
			return true;
		}
		var box = METAcraftContextParameters.getBoundingBox(lootContext).orElse(null);
		if (box == null) return false;
		BlockPos bottomCorner = BlockPos.containing(
				box.minX-5,
				box.minY,
				box.minZ-5
		);
		BlockPos topCorner = BlockPos.containing(
				box.maxX+5,
				box.minY,
				box.maxZ+5
		);
		for (var pos : BlockPos.betweenClosed(bottomCorner, topCorner)) {
			int groundY = lootContext.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
			if (pos.getY() - groundY < 5) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void validate(ValidationContext reporter) {
		LootItemCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityOrBlockEntity(reporter, METAcraftContextParameters.BOUNDING_BOX);
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}
}
