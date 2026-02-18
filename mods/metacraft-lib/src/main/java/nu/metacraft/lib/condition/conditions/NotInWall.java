package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.condition.METAcraftContextParameters;

public class NotInWall implements LootItemCondition {

	private static final NotInWall INSTANCE = new NotInWall();

	public static final MapCodec<NotInWall> CODEC = MapCodec.unit(INSTANCE);

	public static NotInWall getInstance() {
		return INSTANCE;
	}

	private NotInWall() {}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.NOT_IN_WALL;
	}

	@Override
	public boolean test(LootContext lootContext) {
		return METAcraftContextParameters.getBoundingBox(lootContext).map(
				box -> lootContext.getLevel().noCollision(box)
		).orElse(false);
	}

	@Override
	public void validate(ValidationContext reporter) {
		LootItemCondition.super.validate(reporter);
		METAcraftContextParameters.validateEntityOrBlockEntity(reporter, METAcraftContextParameters.BOUNDING_BOX);
	}
}
