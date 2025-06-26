package se.datasektionen.mc.metacraft_core.block.entities;

import com.mojang.serialization.JavaOps;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_lib.util.SoundEffect;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;

import java.util.Map;
import java.util.Optional;

public class TrapSpawnerEntity extends DisguisedBlockEntity {

	private static final String ENTITIES = "Entities";
	private static final String SPAWN_COUNT = "SpawnCount";
	private static final String SOUND_EFFECT = "SoundEffect";
	private static final String TRIGGER_ON_INTERACTION = "TriggerOnInteraction";
	private static final String TRIGGER_ON_BREAK = "TriggerOnBreak";
	private static final String TRIGGER_ON_STEP = "TriggerOnStep";

	private Pool<EntityHelper.SpawnEntry> entities = Pool.of(
			new EntityHelper.SpawnEntry(
					(NbtCompound) JavaOps.INSTANCE.convertTo(
							NbtOps.INSTANCE, Map.of(
									"id", "pig"
							)
					), true, false, new EntityHelper.SpawnEntry.SpawnRules(
							Optional.empty(), SpawnReason.STRUCTURE,
							ConstantIntProvider.create(0), ConstantIntProvider.create(0)
					),
					Optional.empty()
			)
	);
	private int spawnCount = 1;
	private boolean triggerOnInteraction = true;
	private boolean triggerOnBreak = true;
	private boolean triggerOnStep = true;

	private Pool<SoundEffect> soundEffect = Pool.of(new SoundEffect(
			Registries.SOUND_EVENT.getEntry(SoundEvents.BLOCK_IRON_DOOR_OPEN),
			SoundCategory.HOSTILE, 1, 1
	));

	private boolean triggered = false;

	protected TrapSpawnerEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public TrapSpawnerEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.TRAP_SPAWNER, pos, state);
	}

	public void spawnEntity(ServerWorld world, Vec3d pos, Random random, Entity target) {
		for (int i = 0; i < spawnCount; i++) {
			entities.getOrEmpty(random).ifPresent(entity -> {
				EntityHelper.spawnEntity(
						entity, e -> true, e -> true, pos,
						world, random, null, e -> Optional.ofNullable(target)
				);
			});
		}
		playSound(random);
	}

	public void playSound(Random random) {
		if (world != null) {
			soundEffect.getOrEmpty(random).ifPresent(sound -> {
				sound.playSound(world, pos);
			});
		}
	}

	private void trigger(Vec3d spawnPos, Entity target) {
		if (triggered) return;
		if (world instanceof ServerWorld sw) {
			spawnEntity(sw, spawnPos, world.getRandom(), target);
		}
		triggered = true;
	}

	private void triggerThenRemoveBlock(Vec3d spawnPos, Entity target) {
		trigger(spawnPos, target);
		if (world != null) {
			world.setBlockState(pos, state);
		}
	}

	public void triggerRemove() {
		if (!triggerOnBreak) return;
		trigger(Vec3d.ofBottomCenter(pos), null);
	}

	public void triggerStep(Entity entity) {
		if (!triggerOnStep) return;
		triggerThenRemoveBlock(entity.getPos(), entity);
	}

	public ActionResult triggerInteract(Direction side, Entity entity) {
		if (!triggerOnInteraction) return ActionResult.PASS;
		triggerThenRemoveBlock(Vec3d.ofBottomCenter(pos.offset(side)), entity);
		return ActionResult.SUCCESS;
	}

	@Override
	protected void readData(ReadView nbt) {
		super.readData(nbt);
		nbt.read(ENTITIES, EntityHelper.SpawnEntry.POOL_CODEC).ifPresentOrElse(
				pool -> this.entities = pool,
				() -> this.entities = Pool.empty()
		);
		nbt.read(SOUND_EFFECT, SoundEffect.POOL_CODEC).ifPresentOrElse(
				pool -> this.soundEffect = pool,
				() -> this.soundEffect = Pool.empty()
		);
		spawnCount = nbt.getInt(SPAWN_COUNT, 1);
		triggerOnInteraction = nbt.getBoolean(TRIGGER_ON_INTERACTION, true);
		triggerOnBreak = nbt.getBoolean(TRIGGER_ON_BREAK, true);
		triggerOnStep = nbt.getBoolean(TRIGGER_ON_STEP, true);
	}

	@Override
	protected void writeData(WriteView nbt) {
		super.writeData(nbt);
		nbt.put(ENTITIES, EntityHelper.SpawnEntry.POOL_CODEC, entities);
		nbt.put(SOUND_EFFECT, SoundEffect.POOL_CODEC, soundEffect);
		nbt.putInt(SPAWN_COUNT, spawnCount);
		nbt.putBoolean(TRIGGER_ON_INTERACTION, triggerOnInteraction);
		nbt.putBoolean(TRIGGER_ON_BREAK, triggerOnBreak);
		nbt.putBoolean(TRIGGER_ON_STEP, triggerOnStep);
	}
}
