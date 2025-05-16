package se.datasektionen.mc.metacraft_season_4.entity.entities.bosses;

import com.mojang.serialization.Codec;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DataPool;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_core.music.ManageableServerBossBar;
import se.datasektionen.mc.metacraft_core.util.helper.BossBarHelper;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.metacraft.bosses.boss.AutoAttackingBoss;
import se.metacraft.bosses.boss.attacks.Attack;
import se.metacraft.bosses.util.DoubleTeamHandler;

import java.util.Optional;

public class GenericBossPlayer extends PlayerMob implements AutoAttackingBoss {

	private static final String ATTACKS = "attacks";
	private static final Codec<DataPool<Attack>> ATTACK_POOL_CODEC = DataPool.createEmptyAllowedCodec(Attack.REGISTRY_CODEC);

	protected final AttackContainer container = new AttackContainer(this);
	protected DataPool<Attack> attacks;

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
	}

	@Override
	public int getMaxAttacks() {
		return 10;
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
		return attacks.getDataOrEmpty(random);
	}

	@Override
	public Entity getAsEntity() {
		return this;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.put(
				ATTACKS,
				ATTACK_POOL_CODEC.encodeStart(
						getRegistryManager().getOps(NbtOps.INSTANCE),
						attacks
				).getOrThrow()
		);
		container.writeNBT(nbt);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains(ATTACKS)) {
			attacks = ATTACK_POOL_CODEC.parse(
					getRegistryManager().getOps(NbtOps.INSTANCE),
					nbt.get(ATTACKS)
			).resultOrPartial(
					Season4.LOGGER::error
			).orElseGet(() -> DataPool.<Attack>empty());
		} else {
			attacks = DataPool.<Attack>empty();
		}
		container.readNBT(nbt);
	}

	protected DataPool<Attack> createDefaultAttacks(RegistryWrapper.WrapperLookup lookup) {
		return DataPool.<Attack>empty();
	}

	protected ManageableServerBossBar createDefaultBossBar() {
		return new ManageableServerBossBar(
				getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_6
		);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		var data = super.initialize(world, difficulty, spawnReason, entityData);
		((EntityExtensions) this).metacraft_lib$setPreventEnterVehicle(true);
		((EntityExtensions) this).metacraft_lib$setHideUUIDInTooltip(true);
		if (data instanceof DoubleTeamHandler h) {
			attacks = DataPool.<Attack>empty();
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
