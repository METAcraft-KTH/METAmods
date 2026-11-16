package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.content.entity.PunkphozEntities;
import se.metacraft.ovvar.content.entity.PunkphozSpawning;

/**
 * Punkphöz turns up on top of vanilla's wanderers, and every vanilla visit still happens: only on an attempt whose own roll
 * failed, through vanilla's {@code spawn} (llamas, despawn delay and home included, its one-in-ten gate skipped),
 * near each roll's player.
 */
@Mixin(WanderingTraderSpawner.class)
public abstract class WanderingTraderSpawnerMixin {
	@Shadow
	private boolean spawn(ServerLevel level) {
		return false;
	}

	/**
	 * Vanilla's roll, returned unchanged. In 26.3 {@code tick}'s int locals are slot 4 (the new spawn delay),
	 * slot 5 ({@code chanceToSpawn}, the pre-ramp value the roll is compared with) and slot 6 (the new chance),
	 * so {@code chance} is int ordinal 1. When the roll fails, vanilla's {@code if_icmple} then fails too and
	 * {@code tick} returns, so its {@code setSpawnChance(25)} reset never runs for him.
	 */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int ovvar$punkphozRoll(RandomSource random, int bound, Operation<Integer> original,
			@Local(ordinal = 1) int chance, @Local(argsOnly = true) ServerLevel level) {
		int roll = original.call(random, bound);
		if (roll <= chance) return roll;
		PunkphozConfig config = OvvarConfig.get().punkphoz();
		if (!config.enabled()) return roll;
		int loaded = level.getEntities(PunkphozEntities.PUNKPHOZ, e -> true).size();
		for (ServerPlayer target : PunkphozSpawning.targets(level.getPlayers(LivingEntity::isAlive), loaded, config, level.getRandom())) {
			PunkphozSpawning.TARGET.set(target);
			try {
				spawn(level);
			} finally {
				PunkphozSpawning.TARGET.remove();
			}
		}
		return roll;
	}

	/** In 26.3 {@code spawn}'s only {@code GETSTATIC EntityTypes.WANDERING_TRADER}, just before {@code EntityType.spawn}. */
	@ModifyExpressionValue(method = "spawn(Lnet/minecraft/server/level/ServerLevel;)Z", at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/entity/EntityTypes;WANDERING_TRADER:Lnet/minecraft/world/entity/EntityType;", opcode = Opcodes.GETSTATIC))
	private EntityType<?> ovvar$punkphozType(EntityType<?> original) {
		return PunkphozSpawning.TARGET.get() != null ? PunkphozEntities.PUNKPHOZ : original;
	}

	/** Each roll lands near its own player. */
	@WrapOperation(method = "spawn(Lnet/minecraft/server/level/ServerLevel;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getRandomPlayer()Lnet/minecraft/server/level/ServerPlayer;"))
	private ServerPlayer ovvar$punkphozNear(ServerLevel level, Operation<ServerPlayer> original) {
		ServerPlayer target = PunkphozSpawning.TARGET.get();
		return target != null ? target : original.call(level);
	}

	/**
	 * Vanilla's one-in-ten gate ({@code nextInt(10) == 0}), passed for him. In 26.3 it is {@code spawn}'s only
	 * {@code nextInt(I)I}; the position search's random calls are in {@code findSpawnPositionNear}.
	 */
	@WrapOperation(method = "spawn(Lnet/minecraft/server/level/ServerLevel;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int ovvar$punkphozGate(RandomSource random, int bound, Operation<Integer> original) {
		return PunkphozSpawning.TARGET.get() != null ? 0 : original.call(random, bound);
	}
}
