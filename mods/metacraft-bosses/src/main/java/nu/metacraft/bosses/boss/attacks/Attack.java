package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.boss.AutoAttackingBoss;

import java.util.Optional;
import java.util.OptionalInt;

public interface Attack {

	Codec<Attack> REGISTRY_CODEC = AttackRegistry.REGISTRY.getCodec().dispatch(
			Attack::getType, AttackType::codec
	);

	void activate(BossContext<?> ctx);
	void tick(BossContext<?> ctx);
	void deactivate(BossContext<?> ctx);

	default boolean isInstant() {
		return false;
	}

	default boolean isMovement() {
		return false;
	}

	default boolean compatibleWith(Attack attack) {
		return !this.getClass().isInstance(attack);
	}

	default void removeSubAttack(BossContext<?> ctx, Attack attack) {}

	default OptionalInt getDelayOverride(BossContext<?> ctx) {
		return OptionalInt.empty();
	}

	AttackType getType();

	record BossContext<T extends LivingEntity & AutoAttackingBoss>(T boss, Random random) {
		public ServerWorld getWorld() {
			return boss.getServerWorld();
		}

		public Context toNormalContext() {
			return Context.forEntity(boss, random);
		}

		public LootContext toVanillaContext() {
			return METAcraftContexTypes.createTickContext(boss.getServerWorld(), boss, random);
		}

		public Optional<MobEntity> getAsMob() {
			return boss instanceof MobEntity mob ? Optional.of(mob) : Optional.empty();
		}

	}

	record Context(Optional<Entity> entity, ServerWorld world, Vec3d pos, BlockPos blockPos, EntityType<?> entityType, Random random) {

		public static Context forEntity(Entity entity, Random random) {
			return new Context(Optional.of(entity), (ServerWorld) entity.getWorld(), entity.getPos(), entity.getBlockPos(), entity.getType(), random);
		}

		public Box getBoundingBox() {
			return entity.map(Entity::getBoundingBox).orElse(entityType.getSpawnBox(pos.x, pos.y, pos.z));
		}

		public Optional<LivingEntity> getAsLivingEntity() {
			return entity.filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e);
		}

		public Optional<MobEntity> getAsMob() {
			return entity.filter(e -> e instanceof MobEntity).map(e -> (MobEntity) e);
		}
	}

	default Attack copy(RegistryWrapper.WrapperLookup lookup) {
		var ops = lookup.getOps(JavaOps.INSTANCE);
		return REGISTRY_CODEC.parse(ops, REGISTRY_CODEC.encodeStart(ops, this).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).get()).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).get();
	}
}
