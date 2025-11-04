package nu.metacraft.bosses.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.util.helper.EntityAIHelper;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.util.EntityTarget;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.boss.attacks.SpawnEntityAttackBase;
import nu.metacraft.bosses.entity.BossEntities;
import nu.metacraft.bosses.mixin.OminousItemSpawnerAccessor;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public class ItemSpawnerWithTarget extends OminousItemSpawner implements PolymerEntity, EntityTarget.CanSetOwner, EntityTarget.CanSetTarget {

	private static final String OWNER = "Owner";
	private static final String TARGET = "Target";
	private static final String SPAWN_DELAY = "SpawnDelay";
	private static final String PROJECTILE_OVERRIDE = "projectile_override";

	private final EntityTarget owner = EntityTarget.create(level(), new EntityTarget.Context(
			false, false, false, this::discard
	));
	private final EntityTarget target = EntityTarget.create(level(), new EntityTarget.Context(
			false, true, false, this::discard
	));

	private ProjectileOverride projectileOverride = null;

	public ItemSpawnerWithTarget(EntityType<? extends OminousItemSpawner> entityType, Level world) {
		super(entityType, world);
	}

	private static CompoundTag setData(CompoundTag nbt, ItemStack stack, IntProvider delay, ProjectileOverride override, HolderLookup.Provider lookup) {
		IntProvider.POSITIVE_CODEC.encodeStart(NbtOps.INSTANCE, delay).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).ifPresent(d -> nbt.put(SPAWN_DELAY, d));
		if (!stack.isEmpty()) {
			nbt.store(
					OminousItemSpawnerAccessor.getItemKey(), ItemStack.CODEC,
					lookup.createSerializationContext(NbtOps.INSTANCE), stack
			);
		}
		if (override != null) {
			nbt.put(
					PROJECTILE_OVERRIDE,
					ProjectileOverride.CODEC.encodeStart(
							lookup.createSerializationContext(NbtOps.INSTANCE),
							override
					).getOrThrow()
			);
		}
		return nbt;
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, HolderLookup.Provider lookup
	) {
		return createSpawnEntry(item, UniformInt.of(60, 120), horizontalSpawnRange, verticalSpawnRange, null, lookup);
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, ProjectileOverride override, HolderLookup.Provider lookup
	) {
		return createSpawnEntry(item, UniformInt.of(60, 120), horizontalSpawnRange, verticalSpawnRange, override, lookup);
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider delay, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, ProjectileOverride override, HolderLookup.Provider lookup
	) {
		return new EntityHelper.SpawnEntry(
				SpawnEntityAttackBase.createEntityNBTFrom(
						BossEntities.OMINOUS_SPAWNER_WITH_TARGET,
						setData(new CompoundTag(), item, delay, override, lookup)
				),
				false, false,
				new EntityHelper.SpawnEntry.SpawnRules(
						Optional.of(NotInWall.getInstance()), EntitySpawnReason.REINFORCEMENT,
						horizontalSpawnRange, verticalSpawnRange
				),
				Optional.empty()
		);
	}

	@Override
	public void readAdditionalSaveData(ValueInput nbt) {
		super.readAdditionalSaveData(nbt);
		owner.readNBT(nbt, OWNER);
		target.readNBT(nbt, TARGET);
		projectileOverride = nbt.read(
				PROJECTILE_OVERRIDE, ProjectileOverride.CODEC
		).orElse(null);
		nbt.read(SPAWN_DELAY, IntProvider.POSITIVE_CODEC).ifPresent(
				range -> setSpawnItemsAfterTicks(range.sample(random))
		);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput nbt) {
		super.addAdditionalSaveData(nbt);
		owner.writeNBT(nbt, OWNER);
		target.writeNBT(nbt, TARGET);
		nbt.storeNullable(PROJECTILE_OVERRIDE, ProjectileOverride.CODEC, projectileOverride);
	}

	@Override
	public void setTarget(Entity target) {
		this.target.set(target);
	}

	@Override
	public void setOwner(Entity owner) {
		this.owner.set(owner);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.OMINOUS_ITEM_SPAWNER;
	}

	public void setItem(ItemStack stack) {
		((OminousItemSpawnerAccessor) this).callSetItem(stack);
	}

	public void setSpawnItemsAfterTicks(long ticks) {
		((OminousItemSpawnerAccessor) this).setSpawnItemAfterTicks(ticks);
	}

	@Override
	public Entity metacraft_bosses$getSpawnOverride() {
		if (projectileOverride != null) {
			try (
				var logging = LoggingErrorReporter.create(
						() -> "metacraft:ItemSpawnerWithTarget#metacraft_bosses$getSpawnOverride",
						METAcraftBosses.LOGGER
				)
			) {
				var readView = TagValueInput.create(logging, registryAccess(), projectileOverride.entity);
				return EntityHelper.loadEntityWithPassengers(
						readView, level(), EntitySpawnReason.TRIGGERED,
						(e, nbt) -> {
							e.absSnapTo(getX(), getY(), getZ());
							return level().addFreshEntity(e) ? e : null;
						}
				).map(
						e -> {
							if (e instanceof Projectile p) {
								var direction = target.getEntity().map(t -> EntityAIHelper.getDirection(p, t)).orElse(Direction.DOWN.getUnitVec3());
								p.shoot(
										direction.x(), direction.y(), direction.z(),
										projectileOverride.power, projectileOverride.uncertainty
								);
								p.applyOnProjectileSpawned((ServerLevel) level(), getItem());
							}
							return e;
						}
				).orElse(null);
			}
		}
		return null;
	}

	@Override
	public Optional<Vec3> metacraft_bosses$getDirection(Projectile projectile, ProjectileItem.DispenseConfig settings) {
		owner.getEntity().ifPresent(projectile::setOwner);
		return target.getEntity().map(
				entity -> EntityAIHelper.getDirection(projectile, entity)
		);
	}

	@Nullable
	@Override
	public Entity getOwner() {
		return owner.getEntity().orElse(null);
	}

	@Nullable
	@Override
	public LivingEntity getTarget() {
		return (LivingEntity) target.getEntity().filter(e -> e instanceof LivingEntity).orElse(null);
	}

	public record ProjectileOverride(CompoundTag entity, float power, float uncertainty) {
		public static final MapCodec<ProjectileOverride> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						CompoundTag.CODEC.fieldOf("entity").forGetter(ProjectileOverride::entity),
						Codec.FLOAT.fieldOf("power").forGetter(ProjectileOverride::power),
						Codec.FLOAT.fieldOf("uncertainty").forGetter(ProjectileOverride::uncertainty)
				).apply(instance, ProjectileOverride::new)
		);
		public static final Codec<ProjectileOverride> CODEC = MAP_CODEC.codec();
	}
}
