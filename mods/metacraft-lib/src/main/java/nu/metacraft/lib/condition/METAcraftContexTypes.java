package nu.metacraft.lib.condition;

import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.mixin.LootContextParamSetsAccessor;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class METAcraftContexTypes {

	public static final ContextKeySet SPAWN_ENTITY = register(
			"spawn_entity", builder -> builder.required(
					LootContextParams.ORIGIN
			).required(
					METAcraftContextParameters.ENTITY_TYPE
			).required(
					METAcraftContextParameters.BOUNDING_BOX
			).optional(
					METAcraftContextParameters.SPAWN_REASON
			)
	);

	public static final ContextKeySet TICK_ENTITY = register(
			"tick_entity", builder -> builder.required(
					LootContextParams.ORIGIN
			).required(
					LootContextParams.THIS_ENTITY
			).optional(
					LootContextParams.LAST_DAMAGE_PLAYER
			).optional(
					LootContextParams.ATTACKING_ENTITY
			)
	);

	public static LootContext createTickContext(ServerLevel world, Entity entity, RandomSource random) {
		LootParams.Builder parameters = new LootParams.Builder(world).withParameter(
				LootContextParams.THIS_ENTITY, entity
		).withParameter(
				LootContextParams.ORIGIN, entity.position()
		).withOptionalParameter(
				LootContextParams.ATTACKING_ENTITY, entity instanceof LivingEntity living ? living.getLastAttacker() : null
		);
		if (entity instanceof LivingEntity living && living.getLastAttacker() instanceof Player p) {
			parameters.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p);
		}
		return new LootContext.Builder(parameters.create(METAcraftContexTypes.TICK_ENTITY)).withOptionalRandomSource(random).create(Optional.empty());
	}

	public static void init() {

	}

	private static ContextKeySet register(String id, Consumer<ContextKeySet.Builder> type) {
		ContextKeySet.Builder builder = new ContextKeySet.Builder();
		type.accept(builder);
		ContextKeySet lootContextType = builder.build();
		Identifier identifier = METAcraftLib.getID(id);
		ContextKeySet lootContextType2 = LootContextParamSetsAccessor.getMap().put(identifier, lootContextType);
		if (lootContextType2 != null) {
			throw new IllegalStateException("Loot table parameter set " + identifier + " is already registered");
		}
		return lootContextType;
	}

}
