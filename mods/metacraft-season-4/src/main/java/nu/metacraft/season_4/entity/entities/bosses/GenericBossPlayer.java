package nu.metacraft.season_4.entity.entities.bosses;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.core.entity.entities.player_mob.PlayerBrain;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.entity_ref.SelfRef;
import nu.metacraft.core.music.ManageableServerBossBar;
import nu.metacraft.core.position_ref.AtEntityRef;
import nu.metacraft.core.position_ref.RandomRangeWithGravity;
import nu.metacraft.core.position_ref.WithTries;
import nu.metacraft.core.util.helper.BossBarHelper;
import nu.metacraft.lib.extensions.EntityExtensions;
import nu.metacraft.season_4.entity.ai.Season4Sensors;
import nu.metacraft.bosses.boss.AutoAttackingBoss;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.TeleportAttack;
import nu.metacraft.bosses.boss.attacks.target.PositionRefTarget;
import nu.metacraft.bosses.util.DoubleTeamHandler;

import java.util.Optional;

public class GenericBossPlayer extends PlayerMob implements AutoAttackingBoss {

	private static final String ATTACKS = "attacks";
	private static final Codec<Pool<Attack>> ATTACK_POOL_CODEC = Pool.createCodec(Attack.REGISTRY_CODEC);

	protected final AttackContainer container = new AttackContainer(this);
	protected Pool<Attack> attacks;

	private int stuckTime = 0;
	private BlockPos prevPos = null;

	public static final Attack TELEPORT = createTeleport(METAcraftEntities.PLAYER.getDimensions().getBoxAt(Vec3d.ZERO));

	public static final ImmutableList<SensorType<? extends Sensor<? super PlayerMob>>> SENSOR_TYPES = new ImmutableList.Builder<SensorType<? extends Sensor<? super PlayerMob>>>().addAll(
			PlayerBrain.SENSOR_TYPES
	).add(Season4Sensors.TARGET_ENTITY_SENSOR).build();

	public static Attack createTeleport(Box hitbox) {
		return new TeleportAttack(
			new PositionRefTarget(
				new WithTries(
					new RandomRangeWithGravity(
						new AtEntityRef(
							SelfRef.getInstance(),
							Vec3d.ZERO, Vec3d.ZERO, false
						),
						hitbox, Optional.empty(),
						NumberRange.IntRange.between(-10, 10),
						UniformFloatProvider.create(10, 15)
					),
					5
				)
			),
			Optional.empty()
		);
	}

	public GenericBossPlayer(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected boolean getDefaultCanWander() {
		return false;
	}

	@Override
	public void mobTick(ServerWorld world) {
		super.mobTick(world);
		container.tickAttackDelay();
		container.tickAttacks();

		if (getBlockPos().equals(prevPos) && this.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) {
			stuckTime++;

			if (stuckTime > 200) {
				addAttack(TELEPORT);
				stuckTime = 0;
			}
		} else {
			prevPos = getBlockPos();
			stuckTime = 0;
		}

		if (this.getPos().getY() < world.getBottomY()) {
			var targets = getTargets(p -> p.getY() >= world.getBottomY());
			if (targets.isEmpty()) {
				targets = getAllies(TypeFilter.instanceOf(Entity.class), e -> e.getY() >= world.getBottomY());
			}
			if (!targets.isEmpty()) {
				var target = targets.get(random.nextInt(targets.size()));
				double x = this.getX() + (this.random.nextDouble() - (double)0.5F) * (double)8.0F - target.getX() * (double)16.0F;
				double y = this.getY() + (double)(this.random.nextInt(16) - 8) - target.getY() * (double)16.0F;
				double z = this.getZ() + (this.random.nextDouble() - (double)0.5F) * (double)8.0F - target.getZ() * (double)16.0F;
				if (teleport(x, y, z, true)) {
					playSound(SoundEvents.ENTITY_PLAYER_TELEPORT);
				}
			}
		}
	}

	@Override
	protected Brain.Profile<PlayerMob> createBrainProfile() {
		return Brain.createProfile(PlayerBrain.MEMORY_MODULE_TYPES, SENSOR_TYPES);
	}

	@Override
	public int getMaxAttacks() {
		return 10;
	}

	public boolean surviveWith1HP(DamageSource source) {
		return !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean metacraft_season_4$surviveDeath(DamageSource source) {
		if (surviveWith1HP(source)) {
			setHealth(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (surviveWith1HP(source) && getHealth() <= 1) {
			return false;
		}
		return super.damage(world, source, amount);
	}

	@Override
	public AttackContainer getAttacks() {
		return container;
	}

	@Override
	public Attack.BossContext<?> getContext(Attack attack) {
		return new Attack.BossContext<>(this, random);
	}

	@Override
	public int getNewAttackDelay(Attack chosen) {
		return 500;
	}

	@Override
	public Optional<Attack> chooseAttack() {
		return attacks.getOrEmpty(random);
	}

	@Override
	public Entity getAsEntity() {
		return this;
	}

	@Override
	public void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
		nbt.put(
				ATTACKS,
				ATTACK_POOL_CODEC,
				attacks
		);
		container.writeNBT(nbt);
	}

	@Override
	public void readCustomData(ReadView nbt) {
		super.readCustomData(nbt);
		attacks = nbt.read(ATTACKS, ATTACK_POOL_CODEC).orElse(Pool.empty());
		container.readNBT(nbt);
	}

	protected Pool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return Pool.<Attack>empty();
	}

	protected ManageableServerBossBar createDefaultBossBar() {
		return new ManageableServerBossBar(
				getDisplayName(), BossBar.Color.WHITE, BossBar.Style.NOTCHED_6
		);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		var data = super.initialize(world, difficulty, spawnReason, entityData);
		((EntityExtensions) this).metacraft_lib$setPreventEnterVehicle(true);
		((EntityExtensions) this).metacraft_lib$setHideUUIDInTooltip(true);
		this.setPersistent();
		if (data instanceof DoubleTeamHandler h) {
			attacks = Pool.empty();
			this.setLeftHanded(h.primary() instanceof MobEntity m ? m.isLeftHanded() : this.isLeftHanded());
		} else {
			this.attacks = createDefaultAttacks(world.getRegistryManager());
			if (BossBarHelper.getBossBar(this).isEmpty()) {
				BossBarHelper.setBossBar(
						this, createDefaultBossBar()
				);
			}
		}
		return data;
	}
}
