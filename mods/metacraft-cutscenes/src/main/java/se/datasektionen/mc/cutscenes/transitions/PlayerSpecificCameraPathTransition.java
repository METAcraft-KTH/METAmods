package se.datasektionen.mc.cutscenes.transitions;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;
import se.datasektionen.mc.metacraft_core.position_ref.Fixed;
import se.datasektionen.mc.metacraft_core.position_ref.PositionRef;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.metacraft_core.registry.RotationRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.metacraft_core.rotation_ref.FixedRot;
import se.datasektionen.mc.metacraft_core.rotation_ref.RotationRef;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.*;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.DoubleStream;

public class PlayerSpecificCameraPathTransition implements Transition {

	public static final MapCodec<PlayerSpecificCameraPathTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config)
			).apply(instance, PlayerSpecificCameraPathTransition::new)
	);

	private final Config config;
	private final Map<UUID, InterpolationSet<CutsceneContext, DynamicTarget>> interpolationSets = new HashMap<>();

	public PlayerSpecificCameraPathTransition(Config config) {
		this.config = config;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!interpolationSets.containsKey(player.getUuid())) {
			var interpolationSet = config.targets().getTargets(interval);
			var target = DynamicTarget.fromEntity(player);
			interpolationSet = interpolationSet.setStartIfNotPresent(target);
			interpolationSet = interpolationSet.setEndIfNotPresent(target);
			interpolationSets.put(player.getUuid(), interpolationSet);
		}
	}

	private static final String MARKER_ID_PREFIX = "metacraft$player_smooth_camera_marker_";

	private void setLinearInterpolationDuration(Entity display, int duration) {
		var data = display.writeNbt(new NbtCompound());
		data.putInt(DisplayEntity.TELEPORT_DURATION_KEY, duration);
		display.readNbt(data);
	}

	private int getAdjustedTime(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (cutscene.getCurrentTime() == interval.getStart()) {
			return cutscene.getCurrentTime();
		}
		return cutscene.getCurrentTime() + config.interpolationDuration();
	}

	private static String getMarkerID(ServerPlayerEntity player) {
		return MARKER_ID_PREFIX + player.getUuidAsString();
	}

	private void moveEntityToTarget(DynamicTarget target, Entity entity, ServerPlayerEntity player, CutsceneInstance cutscene) {
		var ctx = cutscene.createRefContext(player);
		var pos = target.pos.get(ctx).orElse(player.getPos().subtract(0, EntityType.PLAYER.getDimensions().eyeHeight(), 0));
		var facing = target.rot.get(ctx).orElse(player.getRotationClient());
		entity.updatePositionAndAngles(
				pos.x, pos.y + EntityType.PLAYER.getDimensions().eyeHeight(), pos.z, facing.y, facing.x
		);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			var ctx = new CutsceneContext(player, cutscene);
			cutscene.getRootEntity(getMarkerID(player)).ifPresentOrElse(marker -> {
				if (player.isSpectator()) {
					player.setCameraEntity(marker);
				}
			}, () -> {
				var display = EntityType.TEXT_DISPLAY.create(cutscene.getCutsceneWorld(), SpawnReason.TRIGGERED);
				setLinearInterpolationDuration(display, config.interpolationDuration());
				((EntityExtension) display).metacraft$setHasAccurateMovement(true);
				var target = interpolationSets.get(player.getUuid()).interpolate(ctx, 0);
				moveEntityToTarget(target, display, player, cutscene);
				cutscene.addEntity(getMarkerID(player), display);
			});

			int currentTimeAdjusted = getAdjustedTime(cutscene, interval);
			if (currentTimeAdjusted > interval.getEnd()) {
				cutscene.getRootEntity(getMarkerID(player)).ifPresent(marker -> {
					setLinearInterpolationDuration(marker, config.interpolationDuration() - (currentTimeAdjusted - interval.getEnd()));
				});
				return;
			}
			double delta = ((double) currentTimeAdjusted - interval.getStart()) / interval.getLength();
			var target = interpolationSets.get(player.getUuid()).interpolate(ctx, delta);
			cutscene.getRootEntity(getMarkerID(player)).ifPresent(entity -> {
				if ((cutscene.getCurrentTime() - interval.getStart()) % config.teleportInterval() == 0) {
					moveEntityToTarget(target, entity, player, cutscene);
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.getEntities(getMarkerID(player)).forEach(Entity::discard);
		if (player.isSpectator()) {
			player.setCameraEntity(null);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.PLAYER_CAMERA_PATH;
	}

	public record DynamicTarget(PositionRef pos, RotationRef rot) implements Interpolatable<CutsceneContext> {

		public static final Int2ObjectMap<InterpolationSet.Adjuster> ADJUSTER = Target.ADJUSTER;

		public static final MapCodec<DynamicTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
						RotationRefRegistry.CODEC.fieldOf("rot").forGetter(t -> t.rot)
				).apply(instance, DynamicTarget::new)
		);

		public static final Codec<DynamicTarget> CODEC = MAP_CODEC.codec();

		public static DynamicTarget fromEntity(Entity entity) {
			return new DynamicTarget(new Fixed(entity.getPos()), new FixedRot(entity.getYaw(), entity.getPitch()));
		}

		public static DynamicTarget fromList(DoubleStream stream) {
			var list = stream.limit(5).toArray();
			double x = list[0];
			double y = list[1];
			double z = list[2];
			float yaw = (float) list[3];
			float pitch = (float) list[4];
			return new DynamicTarget(new Fixed(new Vec3d(x, y, z)), new FixedRot(yaw, pitch));
		}

		private Target getEmergencyPoint(CutsceneInstance cutscene) {
			return cutscene.getCutscene().getEntryPoint(null, cutscene).map(
					t -> new Target(t.position(), t.yaw(), t.pitch())
			).orElse(
					cutscene.getCutscene().getExitPoint(null, cutscene).map(
							t -> new Target(t.position(), t.yaw(), t.pitch())
					).orElse(new Target(
							Vec3d.ofBottomCenter(cutscene.getCutsceneWorld().getActualWorld().getSpawnPos()),
							cutscene.getCutsceneWorld().getActualWorld().getSpawnAngle(),
							0
					))
			);
		}

		@Override
		public DoubleList getValues(@Nullable CutsceneContext ctx) {
			if (ctx != null) {
				var player = ctx.player();
				var cutscene = ctx.cutscene();
				if (cutscene != null) {
					var refCtx = ctx.getRefContext();
					var emergencyTarget = Suppliers.memoize(() -> getEmergencyPoint(cutscene));
					var pos = pos().get(refCtx).orElse(player != null ? player.getPos() : emergencyTarget.get().pos());
					var rot = rot().get(refCtx).orElse(player != null ? player.getRotationClient() : new Vec2f(emergencyTarget.get().pitch(), emergencyTarget.get().yaw()));
					return DoubleList.of(pos.getX(), pos.getY(), pos.getZ(), rot.y, rot.x);
				}
			}
			return DoubleList.of(0,0,0,0,0);
		}

		@Override
		public boolean isDynamic() {
			return pos.getType() != PositionRefRegistry.FIXED || rot.getType() != RotationRefRegistry.FIXED;
		}
	}


	public record Config(
			InterpolationSetContainer<DynamicTarget> targets, int interpolationDuration, int teleportInterval
	) implements TransitionConfig {

		public static final MapCodec<InterpolationSetContainer<DynamicTarget>> SMOOTH_PATH = InterpolationSetContainer.createCodec(
				DynamicTarget.MAP_CODEC, DynamicTarget::fromList, DynamicTarget.ADJUSTER
		);

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						SMOOTH_PATH.forGetter(c -> c.targets),
						Codecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(Config::interpolationDuration),
						Codecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(Config::teleportInterval)
				).apply(instance, Config::new)
		);

		@Override
		public Transition create() {
			return new PlayerSpecificCameraPathTransition(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.PLAYER_CAMERA_PATH;
		}

	}
}
