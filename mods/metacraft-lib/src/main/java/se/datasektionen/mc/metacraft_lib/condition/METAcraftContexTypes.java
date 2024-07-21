package se.datasektionen.mc.metacraft_lib.condition;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorLootContextTypes;

import java.util.Optional;
import java.util.function.Consumer;

public class METAcraftContexTypes {

	public static final LootContextType SPAWN_ENTITY = register(
			"spawn_entity", builder -> builder.require(
					LootContextParameters.ORIGIN
			).require(
					METAcraftContextParameters.ENTITY_TYPE
			).require(
					METAcraftContextParameters.BOUNDING_BOX
			).allow(
					METAcraftContextParameters.SPAWN_REASON
			)
	);

	public static final LootContextType TICK_ENTITY = register(
			"tick_entity", builder -> builder.require(
					LootContextParameters.ORIGIN
			).require(
					LootContextParameters.THIS_ENTITY
			).allow(
					LootContextParameters.LAST_DAMAGE_PLAYER
			).allow(
					LootContextParameters.ATTACKING_ENTITY
			)
	);

	public static LootContext createTickContext(ServerWorld world, Entity entity, Random random) {
		LootContextParameterSet.Builder parameters = new LootContextParameterSet.Builder(world).add(
				LootContextParameters.THIS_ENTITY, entity
		).add(
				LootContextParameters.ORIGIN, entity.getPos()
		).addOptional(
				LootContextParameters.ATTACKING_ENTITY, entity instanceof LivingEntity living ? living.getLastAttacker() : null
		);
		if (entity instanceof LivingEntity living && living.getLastAttacker() instanceof PlayerEntity p) {
			parameters.add(LootContextParameters.LAST_DAMAGE_PLAYER, p);
		}
		return new LootContext.Builder(parameters.build(METAcraftContexTypes.TICK_ENTITY)).random(random).build(Optional.empty());
	}

	public static void init() {

	}

	private static LootContextType register(String id, Consumer<LootContextType.Builder> type) {
		LootContextType.Builder builder = new LootContextType.Builder();
		type.accept(builder);
		LootContextType lootContextType = builder.build();
		Identifier identifier = METAcraftLib.getID(id);
		LootContextType lootContextType2 = AccessorLootContextTypes.getMap().put(identifier, lootContextType);
		if (lootContextType2 != null) {
			throw new IllegalStateException("Loot table parameter set " + identifier + " is already registered");
		}
		return lootContextType;
	}

}
