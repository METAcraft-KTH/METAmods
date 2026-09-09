package nu.metacraft.cutscenes.transitions;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.*;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.extension.EntityExtension;
import nu.metacraft.core.position_ref.Fixed;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.core.rotation_ref.FixedRot;
import nu.metacraft.core.rotation_ref.RotationRef;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.core.util.Interpolatable;
import nu.metacraft.core.util.InterpolationSet;
import nu.metacraft.cutscenes.util.CutsceneContext;
import nu.metacraft.cutscenes.util.InterpolationSetContainer;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.util.Target;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.DoubleStream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!interpolationSets.containsKey(player.getUUID())) {
			var interpolationSet = config.targets().getTargets(interval);
			var target = DynamicTarget.fromEntity(player);
			interpolationSet = interpolationSet.setStartIfNotPresent(target);
			interpolationSet = interpolationSet.setEndIfNotPresent(target);
			interpolationSets.put(player.getUUID(), interpolationSet);
		}
	}

	private static final String MARKER_ID_PREFIX = "metacraft$player_smooth_camera_marker_";

	private void setLinearInterpolationDuration(Entity display, int duration) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothCameraPathTransition#setLinearInterpolationDuration", Cutscenes.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, display.registryAccess());
			display.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, duration);
			var readView = TagValueInput.create(logging, display.registryAccess(), data);
			display.load(readView);
		}
	}

	private int getAdjustedTime(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (cutscene.getCurrentTime() == interval.getStart()) {
			return cutscene.getCurrentTime();
		}
		return cutscene.getCurrentTime() + config.interpolationDuration();
	}

	private static String getMarkerID(ServerPlayer player) {
		return MARKER_ID_PREFIX + player.getStringUUID();
	}

	private void moveEntityToTarget(DynamicTarget target, Entity entity, ServerPlayer player, CutsceneInstance cutscene) {
		var ctx = cutscene.createRefContext(player);
		var pos = target.pos.get(ctx).orElse(player.position().subtract(0, EntityTypes.PLAYER.getDimensions().eyeHeight(), 0));
		var facing = target.rot.get(ctx).orElse(player.getRotationVector());
		entity.absSnapTo(
				pos.x, pos.y + EntityTypes.PLAYER.getDimensions().eyeHeight(), pos.z, facing.y, facing.x
		);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			var ctx = new CutsceneContext(player, cutscene);
			cutscene.getRootEntity(getMarkerID(player)).ifPresentOrElse(marker -> {
				if (player.isSpectator()) {
					player.setCamera(marker);
				}
			}, () -> {
				var display = EntityTypes.TEXT_DISPLAY.create(cutscene.getCutsceneWorld(), EntitySpawnReason.TRIGGERED);
				setLinearInterpolationDuration(display, config.interpolationDuration());
				((EntityExtension) display).metacraft$setHasAccurateMovement(true);
				var target = interpolationSets.get(player.getUUID()).interpolate(ctx, 0);
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
			var target = interpolationSets.get(player.getUUID()).interpolate(ctx, delta);
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
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.getEntities(getMarkerID(player)).forEach(Entity::discard);
		if (player.isSpectator()) {
			player.setCamera(null);
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
			return new DynamicTarget(new Fixed(entity.position()), new FixedRot(entity.getYRot(), entity.getXRot()));
		}

		public static DynamicTarget fromList(DoubleStream stream) {
			var list = stream.limit(5).toArray();
			double x = list[0];
			double y = list[1];
			double z = list[2];
			float yaw = (float) list[3];
			float pitch = (float) list[4];
			return new DynamicTarget(new Fixed(new Vec3(x, y, z)), new FixedRot(yaw, pitch));
		}

		private Target getEmergencyPoint(CutsceneInstance cutscene) {
			return cutscene.getCutscene().getEntryPoint(null, cutscene).map(
					t -> new Target(t.position(), t.yRot(), t.xRot())
			).orElse(
					cutscene.getCutscene().getExitPoint(null, cutscene).map(
							t -> new Target(t.position(), t.yRot(), t.xRot())
					).orElse(new Target(
							Vec3.atBottomCenterOf(cutscene.getCutsceneWorld().getActualWorld().getRespawnData().pos()),
							cutscene.getCutsceneWorld().getActualWorld().getRespawnData().yaw(),
							cutscene.getCutsceneWorld().getActualWorld().getRespawnData().pitch()
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
					var pos = pos().get(refCtx).orElse(player != null ? player.position() : emergencyTarget.get().pos());
					var rot = rot().get(refCtx).orElse(player != null ? player.getRotationVector() : new Vec2(emergencyTarget.get().pitch(), emergencyTarget.get().yaw()));
					return DoubleList.of(pos.x(), pos.y(), pos.z(), rot.y, rot.x);
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
						ExtraCodecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(Config::interpolationDuration),
						ExtraCodecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(Config::teleportInterval)
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
