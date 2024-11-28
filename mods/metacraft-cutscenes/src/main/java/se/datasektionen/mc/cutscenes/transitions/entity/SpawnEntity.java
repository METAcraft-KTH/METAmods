package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.cutscenes.Cutscenes;
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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SpawnEntity implements Transition, TransitionConfig {

	public static final Codec<NbtCompound> NBT_CODEC = Codec.withAlternative(
			Codec.STRING.comapFlatMap(line -> {
				try {
					return DataResult.success(StringNbtReader.parse(line));
				} catch (CommandSyntaxException e) {
					return DataResult.error(e::getMessage);
				}
			}, NbtElement::asString),
			NbtCompound.CODEC
	);

	public static final MapCodec<SpawnEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codecs.nonEmptyList(Codec.STRING.listOf()).fieldOf("ids").forGetter(t -> t.ids),
					PositionRefRegistry.CODEC.fieldOf("position").forGetter(t -> t.position),
					NBT_CODEC.fieldOf("nbt").forGetter(t -> t.nbt),
					Codec.BOOL.optionalFieldOf("initialize").forGetter(t -> t.initialize),
					Codec.BOOL.optionalFieldOf("kill_after", false).forGetter(t -> t.killAfter)
			).apply(instance, SpawnEntity::new)
	);

	private final List<String> ids;
	private final PositionRef position;
	private final NbtCompound nbt;
	private final Optional<Boolean> initialize;
	private final boolean killAfter;

	public SpawnEntity(List<String> ids, PositionRef position, NbtCompound nbt, Optional<Boolean> initialize, boolean killAfter) {
		this.ids = ids;
		this.position = position;
		this.nbt = nbt;
		this.initialize = initialize;
		this.killAfter = killAfter;
	}

	public static void spawnEntities(
			List<String> ids, NbtCompound nbt, Optional<Vec3d> pos, CutsceneInstance cutscene, Optional<Boolean> initialize
	) {
		MutableInt idIndex = new MutableInt(0);
		Supplier<String> idGetter = () -> {
			String id = ids.get(idIndex.getValue());
			if (idIndex.getValue() < ids.size()-1) {
				idIndex.increment();
			}
			return id;
		};
		EntityType.loadEntityWithPassengers(nbt, cutscene.getCutsceneWorld(), SpawnReason.EVENT, entity -> {
			pos.ifPresent(entity::setPosition);
			cutscene.addEntity(idGetter.get(), entity);
			if (initialize.orElse(nbt.getSize() > 1) && entity instanceof MobEntity mob) {
				mob.initialize(
						cutscene.getCutsceneWorld(), cutscene.getCutsceneWorld().getLocalDifficulty(entity.getBlockPos()),
						SpawnReason.TRIGGERED, null
				);
				if (nbt.getSize() > 1) {
					entity.readNbt(nbt);
				}
				pos.ifPresent(entity::setPosition);
			}
			return entity;
		});
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var pos = position.get(null, cutscene);
		ids.forEach(id -> {
			if (cutscene.getRootEntity(id).isPresent()) {
				Cutscenes.LOGGER.warn("Warning, an entity with id " + id + " already exists. Your cutscene might behave unexpectedly!");
			}
		});
		spawnEntities(ids, nbt, pos, cutscene, initialize);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var refs = ids.stream().map(CutsceneRef::new).flatMap(e -> e.get(null, cutscene));
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
