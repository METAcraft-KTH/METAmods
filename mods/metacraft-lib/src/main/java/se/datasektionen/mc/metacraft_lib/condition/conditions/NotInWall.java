package se.datasektionen.mc.metacraft_lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContextParameters;

public class NotInWall implements LootCondition {

	private static final NotInWall INSTANCE = new NotInWall();

	public static final MapCodec<NotInWall> CODEC = MapCodec.unit(INSTANCE);

	public static NotInWall getInstance() {
		return INSTANCE;
	}

	private NotInWall() {}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.NOT_IN_WALL;
	}

	@Override
	public boolean test(LootContext lootContext) {
		return METAcraftContextParameters.getBoundingBox(lootContext).map(
				box -> lootContext.getWorld().isSpaceEmpty(box)
		).orElse(false);
	}

	@Override
	public void validate(LootTableReporter reporter) {
		LootCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityOrBlockEntity(reporter, METAcraftContextParameters.BOUNDING_BOX);
	}
}
