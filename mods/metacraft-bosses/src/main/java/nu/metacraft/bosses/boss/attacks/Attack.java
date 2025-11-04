package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.boss.AutoAttackingBoss;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface Attack {

	Codec<Attack> REGISTRY_CODEC = AttackRegistry.REGISTRY.byNameCodec().dispatch(
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

	record BossContext<T extends LivingEntity & AutoAttackingBoss>(T boss, RandomSource random) {
		public ServerLevel getWorld() {
			return boss.getServerWorld();
		}

		public Context toNormalContext() {
			return Context.forEntity(boss, random);
		}

		public LootContext toVanillaContext() {
			return METAcraftContexTypes.createTickContext(boss.getServerWorld(), boss, random);
		}

		public Optional<Mob> getAsMob() {
			return boss instanceof Mob mob ? Optional.of(mob) : Optional.empty();
		}

	}

	record Context(Optional<Entity> entity, ServerLevel world, Vec3 pos, BlockPos blockPos, EntityType<?> entityType, RandomSource random) {

		public static Context forEntity(Entity entity, RandomSource random) {
			return new Context(Optional.of(entity), (ServerLevel) entity.level(), entity.position(), entity.blockPosition(), entity.getType(), random);
		}

		public AABB getBoundingBox() {
			return entity.map(Entity::getBoundingBox).orElse(entityType.getSpawnAABB(pos.x, pos.y, pos.z));
		}

		public Optional<LivingEntity> getAsLivingEntity() {
			return entity.filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e);
		}

		public Optional<Mob> getAsMob() {
			return entity.filter(e -> e instanceof Mob).map(e -> (Mob) e);
		}
	}

	default Attack copy(HolderLookup.Provider lookup) {
		var ops = lookup.createSerializationContext(JavaOps.INSTANCE);
		return REGISTRY_CODEC.parse(ops, REGISTRY_CODEC.encodeStart(ops, this).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).get()).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).get();
	}
}
