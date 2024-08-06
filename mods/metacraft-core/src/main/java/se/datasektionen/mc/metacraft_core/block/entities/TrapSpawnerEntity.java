package se.datasektionen.mc.metacraft_core.block.entities;

import com.mojang.serialization.JavaOps;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_core.mixin.AccessorChunkHolder;
import se.datasektionen.mc.metacraft_core.mixin.AccessorServerChunkManager;
import se.datasektionen.mc.metacraft_lib.util.SoundEffect;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;

import java.util.Map;
import java.util.Optional;

public class TrapSpawnerEntity extends BlockEntity {

	private static final String BLOCK_STATE = "BlockState";
	private static final String ENTITIES = "Entities";
	private static final String SPAWN_COUNT = "SpawnCount";
	private static final String SOUND_EFFECT = "SoundEffect";

	private BlockState state = Blocks.BARRIER.getDefaultState();
	private DataPool<EntityHelper.SpawnEntry> entities = DataPool.of(
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

	private DataPool<SoundEffect> soundEffect = DataPool.of(new SoundEffect(
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

	public BlockState getBlockState() {
		return state;
	}

	public void spawnEntity(ServerWorld world, Vec3d pos, Random random, Entity target) {
		for (int i = 0; i < spawnCount; i++) {
			entities.getDataOrEmpty(random).ifPresent(entity -> {
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
			soundEffect.getDataOrEmpty(random).ifPresent(sound -> {
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
		trigger(Vec3d.ofBottomCenter(pos), null);
	}

	public void triggerStep(Entity entity) {
		triggerThenRemoveBlock(entity.getPos(), entity);
	}

	public void triggerInteract(Direction side, Entity entity) {
		triggerThenRemoveBlock(Vec3d.ofBottomCenter(pos.offset(side)), entity);
	}

	public void setBlockState(BlockState state) {
		if (this.state != state) {
			this.state = state;
			markDirty();
			if (world instanceof ServerWorld sw) {
				var cPos = new ChunkPos(pos);
				var holder = ((AccessorServerChunkManager) sw.getChunkManager()).callGetChunkHolder(
						cPos.toLong()
				);
				var players = ((AccessorChunkHolder) holder).getPlayersWatchingChunkProvider().getPlayersWatchingChunk(
						cPos, false
				);
				for (var player : players) {
					updateClient(player);
				}
			}
		}
	}

	public void updateClient(ServerPlayerEntity player) {
		player.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, state));
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (nbt.contains(BLOCK_STATE)) {
			setBlockState(NbtHelper.toBlockState(
					registryLookup.getWrapperOrThrow(RegistryKeys.BLOCK), nbt.getCompound(BLOCK_STATE)
			));
		}
		if (nbt.contains(ENTITIES)) {
			EntityHelper.SpawnEntry.POOL_CODEC.parse(registryLookup.getOps(NbtOps.INSTANCE), nbt.get(ENTITIES)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(entities -> {
				this.entities = entities;
			});
		}
		if (nbt.contains(SOUND_EFFECT)) {
			SoundEffect.POOL_CODEC.parse(registryLookup.getOps(NbtOps.INSTANCE), nbt.get(SOUND_EFFECT)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(soundEffect -> {
				this.soundEffect = soundEffect;
			});
		}
		if (nbt.contains(SPAWN_COUNT)) {
			spawnCount = nbt.getInt(SPAWN_COUNT);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.put(BLOCK_STATE, NbtHelper.fromBlockState(state));
		EntityHelper.SpawnEntry.POOL_CODEC.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), entities).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(entities -> {
			nbt.put(ENTITIES, entities);
		});
		SoundEffect.POOL_CODEC.encodeStart(registryLookup.getOps(NbtOps.INSTANCE), soundEffect).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(effect -> {
			nbt.put(SOUND_EFFECT, effect);
		});
		nbt.putInt(SPAWN_COUNT, spawnCount);
	}
}
