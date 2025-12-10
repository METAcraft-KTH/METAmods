package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.cutscene.world.CutsceneLevel;
import nu.metacraft.cutscenes.entity_ref.CutsceneRef;
import nu.metacraft.core.entity_ref.SelfRef;
import nu.metacraft.core.position_ref.AtEntityRef;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.core.rotation_ref.CopyFromEntity;
import nu.metacraft.core.rotation_ref.RotationRef;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.lib.util.AccurateSerializableNBT;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class AddPlayerDummies implements Transition, TransitionConfig {

	public static final String PLAYER_REFERENCE = "player";

	public static final PositionRef DEFAULT_POS = new AtEntityRef(SelfRef.getInstance(), Vec3.ZERO, Vec3.ZERO, false);
	public static final RotationRef DEFAULT_ROT = new CopyFromEntity(SelfRef.getInstance());

	public static final MapCodec<AddPlayerDummies> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.nonEmptyList(Codec.STRING.listOf()).optionalFieldOf("ids", List.of(PLAYER_REFERENCE)).forGetter(t -> t.ids),
					PositionRefRegistry.CODEC.optionalFieldOf("position", DEFAULT_POS).forGetter(t -> t.position),
					RotationRefRegistry.CODEC.optionalFieldOf("rotation", DEFAULT_ROT).forGetter(t -> t.rotation),
					Codec.BOOL.optionalFieldOf("include_remote_dim", false).forGetter(t -> t.includeDespiteRemoteDim),
					AccurateSerializableNBT.CODEC.optionalFieldOf("nbt").forGetter(t -> t.nbt),
					Identifier.CODEC.optionalFieldOf("function").forGetter(t -> t.function),
					Removal.CODEC.optionalFieldOf("removal", Removal.DISCARD).forGetter(t -> t.removal)
			).apply(instance, AddPlayerDummies::new)
	);

	public static final Codec<TypedEntityData<EntityType<?>>> ENTITY_DATA_CODEC = TypedEntityData.codec(EntityType.CODEC);

	private final List<String> ids;
	private final PositionRef position;
	private final RotationRef rotation;
	private final boolean includeDespiteRemoteDim;
	private final Optional<AccurateSerializableNBT> nbt;
	private final Optional<Identifier> function;
	private final Removal removal;

	public static final AddPlayerDummies DEFAULT = new AddPlayerDummies(
			List.of(PLAYER_REFERENCE), DEFAULT_POS, DEFAULT_ROT,
			false, Optional.empty(), Optional.empty(), Removal.DISCARD
	);

	public AddPlayerDummies(
			List<String> ids, PositionRef position, RotationRef rotation, boolean includeDespiteRemoteDim,
			Optional<AccurateSerializableNBT> nbt, Optional<Identifier> function, Removal removal
	) {
		this.ids = ids;
		this.position = position;
		this.rotation = rotation;
		this.includeDespiteRemoteDim = includeDespiteRemoteDim;
		this.nbt = nbt;
		this.function = function;
		this.removal = removal;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
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

			entity.get().getSelfAndPassengers().forEach(e -> {
				pos.ifPresent(
						target -> e.absSnapTo(target.x(), target.y(), target.z())
				);
				rotation.get(cutscene.createRefContext(player)).ifPresent(
						target -> e.absSnapRotationTo(target.y, target.x)
				);
				if (e instanceof PlayerMob p) {
					nbt.flatMap(
							nbt -> ENTITY_DATA_CODEC.parse(
									NbtOps.INSTANCE, nbt.getMerged()
							).resultOrPartial(Cutscenes.LOGGER::error)
					).ifPresent(d -> d.loadInto(p));
					function.flatMap(
							function -> player.level().getServer().getFunctions().get(function)
					).ifPresent(function -> {
						player.level().getServer().getFunctions().execute(
								function,
								p.createCommandSourceStackForNameResolution(
										cutscene.getCutsceneWorld()
								).withPermission(LevelBasedPermissionSet.GAMEMASTER)
						);
					});
				}
				cutscene.addEntity(idGetter.get(), e);
			});

			return entity.get();
		});
	}

	public Optional<Entity> createFromData(CompoundTag data, CutsceneLevel world) {
		var player = METAcraftEntities.PLAYER.create(world, EntitySpawnReason.EVENT);
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:AddPlayerDummies#createFromData", Cutscenes.LOGGER)) {
			var readView = TagValueInput.create(logging, world.registryAccess(), data);
			var spawnWorld = PlayerDataHelper.getWorld(world.getServer(), readView);
			if ((spawnWorld.isEmpty() || spawnWorld.get() != world.getActualWorld()) && !includeDespiteRemoteDim) {
				return Optional.empty();
			}
			player.copyFromPlayerData(data);
			PlayerDataHelper.loadRootVehicleAndPassengers(player, readView, e -> e);
			var root = player.getRootVehicle();
			root.getPassengersAndSelf().forEach(e -> {
				if (e instanceof Mob mob) {
					mob.setPersistenceRequired();
				}
			});
			return Optional.of(root);
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (removal != Removal.NONE) {
			var refs = ids.stream().map(CutsceneRef::new).flatMap(e -> e.get(cutscene.getRefContext()));
			if (removal == Removal.KILL) {
				refs.forEach(e -> e.kill(cutscene.getCutsceneWorld()));
			} else {
				refs.forEach(Entity::discard);
			}
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

	public enum Removal implements StringRepresentable {
		NONE("none"),
		DISCARD("discard"),
		KILL("kill");

		public static final Codec<Removal> CODEC = StringRepresentable.fromEnum(Removal::values);

		private final String name;

		Removal(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
