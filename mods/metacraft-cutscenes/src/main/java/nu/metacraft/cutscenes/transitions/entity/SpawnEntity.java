package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.world.CutsceneWorld;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.entity_ref.CutsceneRef;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.lib.util.AccurateSerializableNBT;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.lib.util.helper.ViewHelper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

public class SpawnEntity implements Transition, TransitionConfig {


	public static final MapCodec<SpawnEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.nonEmptyList(Codec.STRING.listOf()).fieldOf("ids").forGetter(t -> t.ids),
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

	public SpawnEntity(List<String> ids, PositionRef position, CompoundTag nbt, Optional<Boolean> initialize, boolean killAfter) {
		this(ids, position, new AccurateSerializableNBT(new CompoundTag(), nbt), initialize, killAfter);
	}

	public SpawnEntity(List<String> ids, PositionRef position, AccurateSerializableNBT nbt, Optional<Boolean> initialize, boolean killAfter) {
		this.ids = ids;
		this.position = position;
		this.nbt = nbt;
		this.initialize = initialize;
		this.killAfter = killAfter;
	}

	public static void spawnEntities(
			List<String> ids, CompoundTag nbt, Optional<Vec3> pos, CutsceneWorld world, Optional<Boolean> initialize
	) {
		MutableInt idIndex = new MutableInt(0);
		Supplier<String> idGetter = () -> {
			String id = ids.get(idIndex.getValue());
			if (idIndex.getValue() < ids.size()-1) {
				idIndex.increment();
			}
			return id;
		};
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SpawnEntity#spawnEntities", Cutscenes.LOGGER)) {
			var readView = TagValueInput.create(logging, world.registryAccess(), nbt);
			EntityHelper.loadEntityWithPassengers(readView, world, EntitySpawnReason.EVENT, (entity, data) -> {
				pos.ifPresent(entity::setPos);
				world.getEntityManager().addEntity(idGetter.get(), entity);
				if (initialize.orElse(ViewHelper.getSize(data) <= 1)) {
					EntityHelper.initializeEntity(
							entity, ViewHelper.getSize(data) > 1 ? data : null,
							world, world.getCurrentDifficultyAt(entity.blockPosition()),
							EntitySpawnReason.TRIGGERED, null
					);
					pos.ifPresent(entity::setPos);
				}
				return entity;
			});
		}
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
