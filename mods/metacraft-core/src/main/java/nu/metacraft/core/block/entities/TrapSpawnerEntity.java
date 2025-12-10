package nu.metacraft.core.block.entities;

import com.mojang.serialization.JavaOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.lib.util.SoundEffect;
import nu.metacraft.lib.util.helper.EntityHelper;

import java.util.Map;
import java.util.Optional;

public class TrapSpawnerEntity extends DisguisedBlockEntity {

	private static final String ENTITIES = "Entities";
	private static final String SPAWN_COUNT = "SpawnCount";
	private static final String SOUND_EFFECT = "SoundEffect";
	private static final String TRIGGER_ON_INTERACTION = "TriggerOnInteraction";
	private static final String TRIGGER_ON_BREAK = "TriggerOnBreak";
	private static final String TRIGGER_ON_STEP = "TriggerOnStep";

	private WeightedList<EntityHelper.SpawnEntry> entities = WeightedList.of(
			new EntityHelper.SpawnEntry(
					(CompoundTag) JavaOps.INSTANCE.convertTo(
							NbtOps.INSTANCE, Map.of(
									"id", "pig"
							)
					), true, false, new EntityHelper.SpawnEntry.SpawnRules(
							Optional.empty(), EntitySpawnReason.STRUCTURE,
							ConstantInt.of(0), ConstantInt.of(0)
					),
					Optional.empty()
			)
	);
	private int spawnCount = 1;
	private boolean triggerOnInteraction = true;
	private boolean triggerOnBreak = true;
	private boolean triggerOnStep = true;

	private WeightedList<SoundEffect> soundEffect = WeightedList.of(new SoundEffect(
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.IRON_DOOR_OPEN),
			SoundSource.HOSTILE, 1, 1
	));

	private boolean triggered = false;

	protected TrapSpawnerEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public TrapSpawnerEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.TRAP_SPAWNER, pos, state);
	}

	public void spawnEntity(ServerLevel world, Vec3 pos, RandomSource random, Entity target) {
		for (int i = 0; i < spawnCount; i++) {
			entities.getRandom(random).ifPresent(entity -> {
				EntityHelper.spawnEntity(
						entity, e -> true, e -> true, pos,
						world, random, null, e -> Optional.ofNullable(target)
				);
			});
		}
		playSound(random);
	}

	public void playSound(RandomSource random) {
		if (level != null) {
			soundEffect.getRandom(random).ifPresent(sound -> {
				sound.playSound(level, worldPosition);
			});
		}
	}

	private void trigger(Vec3 spawnPos, Entity target) {
		if (triggered) return;
		if (level instanceof ServerLevel sw) {
			spawnEntity(sw, spawnPos, level.getRandom(), target);
		}
		triggered = true;
	}

	private void triggerThenRemoveBlock(Vec3 spawnPos, Entity target) {
		trigger(spawnPos, target);
		if (level != null) {
			level.setBlockAndUpdate(worldPosition, state);
		}
	}

	public void triggerRemove() {
		if (!triggerOnBreak) return;
		trigger(Vec3.atBottomCenterOf(worldPosition), null);
	}

	public void triggerStep(Entity entity) {
		if (!triggerOnStep) return;
		triggerThenRemoveBlock(entity.position(), entity);
	}

	public InteractionResult triggerInteract(Direction side, Entity entity) {
		if (!triggerOnInteraction) return InteractionResult.PASS;
		triggerThenRemoveBlock(Vec3.atBottomCenterOf(worldPosition.relative(side)), entity);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		nbt.read(ENTITIES, EntityHelper.SpawnEntry.POOL_CODEC).ifPresentOrElse(
				pool -> this.entities = pool,
				() -> this.entities = WeightedList.of()
		);
		nbt.read(SOUND_EFFECT, SoundEffect.POOL_CODEC).ifPresentOrElse(
				pool -> this.soundEffect = pool,
				() -> this.soundEffect = WeightedList.of()
		);
		spawnCount = nbt.getIntOr(SPAWN_COUNT, 1);
		triggerOnInteraction = nbt.getBooleanOr(TRIGGER_ON_INTERACTION, true);
		triggerOnBreak = nbt.getBooleanOr(TRIGGER_ON_BREAK, true);
		triggerOnStep = nbt.getBooleanOr(TRIGGER_ON_STEP, true);
	}

	@Override
	protected void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		nbt.store(ENTITIES, EntityHelper.SpawnEntry.POOL_CODEC, entities);
		nbt.store(SOUND_EFFECT, SoundEffect.POOL_CODEC, soundEffect);
		nbt.putInt(SPAWN_COUNT, spawnCount);
		nbt.putBoolean(TRIGGER_ON_INTERACTION, triggerOnInteraction);
		nbt.putBoolean(TRIGGER_ON_BREAK, triggerOnBreak);
		nbt.putBoolean(TRIGGER_ON_STEP, triggerOnStep);
	}
}
