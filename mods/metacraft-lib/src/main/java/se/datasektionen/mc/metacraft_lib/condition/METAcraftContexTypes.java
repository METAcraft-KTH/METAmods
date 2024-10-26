package se.datasektionen.mc.metacraft_lib.condition;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextType;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorLootContextTypes;

import java.util.Optional;
import java.util.function.Consumer;

public class METAcraftContexTypes {

	public static final ContextType SPAWN_ENTITY = register(
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

	public static final ContextType TICK_ENTITY = register(
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
		LootWorldContext.Builder parameters = new LootWorldContext.Builder(world).add(
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

	private static ContextType register(String id, Consumer<ContextType.Builder> type) {
		ContextType.Builder builder = new ContextType.Builder();
		type.accept(builder);
		ContextType lootContextType = builder.build();
		Identifier identifier = METAcraftLib.getID(id);
		ContextType lootContextType2 = AccessorLootContextTypes.getMap().put(identifier, lootContextType);
		if (lootContextType2 != null) {
			throw new IllegalStateException("Loot table parameter set " + identifier + " is already registered");
		}
		return lootContextType;
	}

}
