package nu.metacraft.bosses.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.util.helper.EntityAIHelper;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.util.EntityTarget;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.boss.attacks.SpawnEntityAttackBase;
import nu.metacraft.bosses.entity.BossEntities;
import nu.metacraft.bosses.mixin.AccessorOminousItemSpawnerEntity;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public class ItemSpawnerWithTarget extends OminousItemSpawnerEntity implements PolymerEntity, EntityTarget.CanSetOwner, EntityTarget.CanSetTarget {

	private static final String OWNER = "Owner";
	private static final String TARGET = "Target";
	private static final String SPAWN_DELAY = "SpawnDelay";
	private static final String PROJECTILE_OVERRIDE = "projectile_override";

	private final EntityTarget owner = EntityTarget.create(getEntityWorld(), new EntityTarget.Context(
			false, false, false, this::discard
	));
	private final EntityTarget target = EntityTarget.create(getEntityWorld(), new EntityTarget.Context(
			false, true, false, this::discard
	));

	private ProjectileOverride projectileOverride = null;

	public ItemSpawnerWithTarget(EntityType<? extends OminousItemSpawnerEntity> entityType, World world) {
		super(entityType, world);
	}

	private static NbtCompound setData(NbtCompound nbt, ItemStack stack, IntProvider delay, ProjectileOverride override, RegistryWrapper.WrapperLookup lookup) {
		IntProvider.POSITIVE_CODEC.encodeStart(NbtOps.INSTANCE, delay).resultOrPartial(
				METAcraftBosses.LOGGER::error
		).ifPresent(d -> nbt.put(SPAWN_DELAY, d));
		if (!stack.isEmpty()) {
			nbt.put(
					AccessorOminousItemSpawnerEntity.getItemKey(), ItemStack.CODEC,
					lookup.getOps(NbtOps.INSTANCE), stack
			);
		}
		if (override != null) {
			nbt.put(
					PROJECTILE_OVERRIDE,
					ProjectileOverride.CODEC.encodeStart(
							lookup.getOps(NbtOps.INSTANCE),
							override
					).getOrThrow()
			);
		}
		return nbt;
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, RegistryWrapper.WrapperLookup lookup
	) {
		return createSpawnEntry(item, UniformIntProvider.create(60, 120), horizontalSpawnRange, verticalSpawnRange, null, lookup);
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, ProjectileOverride override, RegistryWrapper.WrapperLookup lookup
	) {
		return createSpawnEntry(item, UniformIntProvider.create(60, 120), horizontalSpawnRange, verticalSpawnRange, override, lookup);
	}

	public static EntityHelper.SpawnEntry createSpawnEntry(
			ItemStack item, IntProvider delay, IntProvider horizontalSpawnRange, IntProvider verticalSpawnRange, ProjectileOverride override, RegistryWrapper.WrapperLookup lookup
	) {
		return new EntityHelper.SpawnEntry(
				SpawnEntityAttackBase.createEntityNBTFrom(
						BossEntities.OMINOUS_SPAWNER_WITH_TARGET,
						setData(new NbtCompound(), item, delay, override, lookup)
				),
				false, false,
				new EntityHelper.SpawnEntry.SpawnRules(
						Optional.of(NotInWall.getInstance()), SpawnReason.REINFORCEMENT,
						horizontalSpawnRange, verticalSpawnRange
				),
				Optional.empty()
		);
	}

	@Override
	public void readCustomData(ReadView nbt) {
		super.readCustomData(nbt);
		owner.readNBT(nbt, OWNER);
		target.readNBT(nbt, TARGET);
		projectileOverride = nbt.read(
				PROJECTILE_OVERRIDE, ProjectileOverride.CODEC
		).orElse(null);
		nbt.read(SPAWN_DELAY, IntProvider.POSITIVE_CODEC).ifPresent(
				range -> setSpawnItemsAfterTicks(range.get(random))
		);
	}

	@Override
	public void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
		owner.writeNBT(nbt, OWNER);
		target.writeNBT(nbt, TARGET);
		nbt.putNullable(PROJECTILE_OVERRIDE, ProjectileOverride.CODEC, projectileOverride);
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
		((AccessorOminousItemSpawnerEntity) this).callSetItem(stack);
	}

	public void setSpawnItemsAfterTicks(long ticks) {
		((AccessorOminousItemSpawnerEntity) this).setSpawnItemAfterTicks(ticks);
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
				var readView = NbtReadView.create(logging, getRegistryManager(), projectileOverride.entity);
				return EntityHelper.loadEntityWithPassengers(
						readView, getEntityWorld(), SpawnReason.TRIGGERED,
						(e, nbt) -> {
							e.updatePosition(getX(), getY(), getZ());
							return getEntityWorld().spawnEntity(e) ? e : null;
						}
				).map(
						e -> {
							if (e instanceof ProjectileEntity p) {
								var direction = target.getEntity().map(t -> EntityAIHelper.getDirection(p, t)).orElse(Direction.DOWN.getDoubleVector());
								p.setVelocity(
										direction.getX(), direction.getY(), direction.getZ(),
										projectileOverride.power, projectileOverride.uncertainty
								);
								p.triggerProjectileSpawned((ServerWorld) getEntityWorld(), getItem());
							}
							return e;
						}
				).orElse(null);
			}
		}
		return null;
	}

	@Override
	public Optional<Vec3d> metacraft_bosses$getDirection(ProjectileEntity projectile, ProjectileItem.Settings settings) {
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

	public record ProjectileOverride(NbtCompound entity, float power, float uncertainty) {
		public static final MapCodec<ProjectileOverride> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						NbtCompound.CODEC.fieldOf("entity").forGetter(ProjectileOverride::entity),
						Codec.FLOAT.fieldOf("power").forGetter(ProjectileOverride::power),
						Codec.FLOAT.fieldOf("uncertainty").forGetter(ProjectileOverride::uncertainty)
				).apply(instance, ProjectileOverride::new)
		);
		public static final Codec<ProjectileOverride> CODEC = MAP_CODEC.codec();
	}
}
