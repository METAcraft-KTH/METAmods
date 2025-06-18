package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.CutsceneRef;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.metacraft_lib.util.AccurateSerializableNBT;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SpawnEntity implements Transition, TransitionConfig {


	public static final MapCodec<SpawnEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codecs.nonEmptyList(Codec.STRING.listOf()).fieldOf("ids").forGetter(t -> t.ids),
					PositionRefRegistry.CODEC.fieldOf("position").forGetter(t -> t.position),
					AccurateSerializableNBT.CODEC.fieldOf("nbt").forGetter(t -> t.nbt),
					Codec.BOOL.optionalFieldOf("initialize").forGetter(t -> t.initialize),
					Codec.BOOL.optionalFieldOf("kill_after", false).forGetter(t -> t.killAfter)
			).apply(instance, SpawnEntity::new)
	);

	private final List<String> ids;
	private final PositionRef position;
	private final AccurateSerializableNBT nbt;
	private final Optional<Boolean> initialize;
	private final boolean killAfter;

	public SpawnEntity(List<String> ids, PositionRef position, NbtCompound nbt, Optional<Boolean> initialize, boolean killAfter) {
		this(ids, position, new AccurateSerializableNBT(new NbtCompound(), nbt), initialize, killAfter);
	}

	public SpawnEntity(List<String> ids, PositionRef position, AccurateSerializableNBT nbt, Optional<Boolean> initialize, boolean killAfter) {
		this.ids = ids;
		this.position = position;
		this.nbt = nbt;
		this.initialize = initialize;
		this.killAfter = killAfter;
	}

	public static void spawnEntities(
			List<String> ids, NbtCompound nbt, Optional<Vec3d> pos, CutsceneWorld world, Optional<Boolean> initialize
	) {
		MutableInt idIndex = new MutableInt(0);
		Supplier<String> idGetter = () -> {
			String id = ids.get(idIndex.getValue());
			if (idIndex.getValue() < ids.size()-1) {
				idIndex.increment();
			}
			return id;
		};
		EntityHelper.loadEntityWithPassengers(nbt, world, SpawnReason.EVENT, (entity, data) -> {
			pos.ifPresent(entity::setPosition);
			world.getEntityManager().addEntity(idGetter.get(), entity);
			if (initialize.orElse(data.getSize() <= 1)) {
				EntityHelper.initializeEntity(
						entity, data.getSize() > 1 ? data : null,
						world, world.getLocalDifficulty(entity.getBlockPos()),
						SpawnReason.TRIGGERED, null
				);
				pos.ifPresent(entity::setPosition);
			}
			return entity;
		});
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var pos = position.get(cutscene.getRefContext());
		ids.forEach(id -> {
			if (cutscene.getRootEntity(id).isPresent()) {
				Cutscenes.LOGGER.warn("Warning, an entity with id " + id + " already exists. Your cutscene might behave unexpectedly!");
			}
		});
		spawnEntities(ids, nbt.getMerged(), pos, cutscene.getCutsceneWorld(), initialize);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var refs = ids.stream().map(CutsceneRef::new).flatMap(e -> e.get(cutscene.getRefContext()));
		if (killAfter) {
			refs.forEach(e -> e.kill(cutscene.getCutsceneWorld()));
		} else {
			refs.forEach(Entity::discard);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SPAWN_ENTITY;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SPAWN_ENTITY;
	}
}
