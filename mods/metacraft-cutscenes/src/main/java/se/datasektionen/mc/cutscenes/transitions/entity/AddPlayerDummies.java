package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.cutscene.world.CutsceneWorld;
import se.datasektionen.mc.cutscenes.entity_ref.CutsceneRef;
import se.datasektionen.mc.cutscenes.entity_ref.SelfRef;
import se.datasektionen.mc.cutscenes.position_ref.AtEntityRef;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.rotation_ref.CopyFromEntity;
import se.datasektionen.mc.cutscenes.rotation_ref.RotationRef;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_lib.util.AccurateSerializableNBT;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class AddPlayerDummies implements Transition, TransitionConfig {

	public static final String PLAYER_REFERENCE = "player";

	public static final PositionRef DEFAULT_POS = new AtEntityRef(SelfRef.getInstance(), Vec3d.ZERO, Vec3d.ZERO, false);
	public static final RotationRef DEFAULT_ROT = new CopyFromEntity(SelfRef.getInstance());

	public static final MapCodec<AddPlayerDummies> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codecs.nonEmptyList(Codec.STRING.listOf()).optionalFieldOf("ids", List.of(PLAYER_REFERENCE)).forGetter(t -> t.ids),
					PositionRefRegistry.CODEC.optionalFieldOf("position", DEFAULT_POS).forGetter(t -> t.position),
					RotationRefRegistry.CODEC.optionalFieldOf("rotation", DEFAULT_ROT).forGetter(t -> t.rotation),
					Codec.BOOL.optionalFieldOf("include_remote_dim", false).forGetter(t -> t.includeDespiteRemoteDim),
					AccurateSerializableNBT.CODEC.optionalFieldOf("nbt").forGetter(t -> t.nbt),
					Identifier.CODEC.optionalFieldOf("function").forGetter(t -> t.function),
					Codec.BOOL.optionalFieldOf("kill_after", false).forGetter(t -> t.killAfter)
			).apply(instance, AddPlayerDummies::new)
	);

	private final List<String> ids;
	private final PositionRef position;
	private final RotationRef rotation;
	private final boolean includeDespiteRemoteDim;
	private final Optional<AccurateSerializableNBT> nbt;
	private final Optional<Identifier> function;
	private final boolean killAfter;

	public static final AddPlayerDummies DEFAULT = new AddPlayerDummies(
			List.of(PLAYER_REFERENCE), DEFAULT_POS, DEFAULT_ROT,
			false, Optional.empty(), Optional.empty(), false
	);

	public AddPlayerDummies(
			List<String> ids, PositionRef position, RotationRef rotation, boolean includeDespiteRemoteDim,
			Optional<AccurateSerializableNBT> nbt, Optional<Identifier> function, boolean killAfter
	) {
		this.ids = ids;
		this.position = position;
		this.rotation = rotation;
		this.includeDespiteRemoteDim = includeDespiteRemoteDim;
		this.nbt = nbt;
		this.function = function;
		this.killAfter = killAfter;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		var pos = position.get(cutscene.createRefContext(player));
		MutableInt idIndex = new MutableInt(0);
		Supplier<String> idGetter = () -> {
			String id = ids.get(idIndex.getValue());
			if (idIndex.getValue() < ids.size()-1) {
				idIndex.increment();
			}
			return id;
		};

		cutscene.addPlayerDummy(player, data -> {
			var entity = createFromData(data, cutscene.getCutsceneWorld());
			if (entity.isEmpty()) return null;

			entity.get().streamSelfAndPassengers().forEach(e -> {
				pos.ifPresent(
						target -> e.updatePosition(target.getX(), target.getY(), target.getZ())
				);
				rotation.get(cutscene.createRefContext(player)).ifPresent(
						target -> e.setAngles(target.y, target.x)
				);
				if (e instanceof PlayerMob p) {
					nbt.ifPresent(
							nbt -> NbtComponent.of(nbt.getMerged()).applyToEntity(p)
					);
					function.flatMap(
							function -> player.getServer().getCommandFunctionManager().getFunction(function)
					).ifPresent(function -> {
						player.getServer().getCommandFunctionManager().execute(function, p.getCommandSource(cutscene.getCutsceneWorld()).withLevel(2));
					});
				}
				cutscene.addEntity(idGetter.get(), e);
			});

			return entity.get();
		});
	}

	public Optional<Entity> createFromData(NbtCompound data, CutsceneWorld world) {
		var player = METAcraftEntities.PLAYER.create(world, SpawnReason.EVENT);
		var spawnWorld = PlayerDataHelper.getWorld(world.getServer(), data);
		if ((spawnWorld.isEmpty() || spawnWorld.get() != world.getActualWorld()) && !includeDespiteRemoteDim) {
			return Optional.empty();
		}
		player.copyFromPlayerData(data);
		PlayerDataHelper.loadRootVehicleAndPassengers(player, data, e -> e);
		var root = player.getRootVehicle();
		root.streamPassengersAndSelf().forEach(e -> {
			if (e instanceof MobEntity mob) {
				mob.setPersistent();
			}
		});
		return Optional.of(root);
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
		return TransitionRegistry.PLAYER_DUMMIES;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.PLAYER_DUMMIES;
	}
}
