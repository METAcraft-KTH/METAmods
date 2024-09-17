package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.MovingTransitionConfig;

import java.util.Arrays;
import java.util.Optional;
import java.util.TimerTask;

		public class MovingTransition implements Transition, SmoothMovementTransition, DeltaTickTransition {

	public static final MapCodec<MovingTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					MovingTransitionConfig.CODEC.forGetter(t -> t.config),
					Target.CODEC.optionalFieldOf("start_pos").forGetter(t -> Optional.ofNullable(t.startPos)),
					Codec.LONG.fieldOf("progress").forGetter(t -> t.progress)
			).apply(instance, MovingTransition::new)
	);

	private final MovingTransitionConfig config;
	private Target startPos;

	private Target prevStart;
	private Target nextEnd;

	private TimerTask task;
	private long progress;

	public MovingTransition(MovingTransitionConfig config) {
		this.config = config;
	}

	public MovingTransition(MovingTransitionConfig config, Optional<Target> startPos, long progress) {
		this(config);
		this.startPos = startPos.orElse(null);
		this.progress = progress;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (startPos == null) {
			startPos = cutscene.getTransitions().getValuesAt(interval.getStart()-1).filter(
					movement -> movement instanceof SmoothMovementTransition
			).map(movement -> (SmoothMovementTransition) movement).findAny().map(
					SmoothMovementTransition::getEnd
			).orElse(
					cutscene.copyPlayers().stream().findAny().map(Target::fromPlayer).orElse(
							new Target(Vec3d.ZERO, 0, 0)
					)
			);
		}
	}

	public static double interpolate(double x, double[] p) {
		//https://www.paulinternet.nl/?page=bicubic
		return p[1] + 0.5 * x*(p[2] - p[0] + x*(2.0*p[0] - 5.0*p[1] + 4.0*p[2] - p[3] + x*(3.0*(p[1] - p[2]) + p[3] - p[0])));
	}

	public static Vec3d interpolate(double x, Vec3d[] p) {
		return new Vec3d(
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3d::getX).toArray()),
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3d::getY).toArray()),
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3d::getZ).toArray())
		);
	}

	public static Target interpolate(double x, Target[] p) {
		return new Target(
				interpolate(x, Arrays.stream(p).map(Target::pos).toArray(Vec3d[]::new)),
				(float) interpolate(x, Arrays.stream(p).mapToDouble(Target::yaw).toArray()),
				(float) interpolate(x, Arrays.stream(p).mapToDouble(Target::pitch).toArray())
		);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			boolean send = false;
			if (!player.getAbilities().allowFlying) {
				player.getAbilities().allowFlying = true;
				send = true;
			}
			if (!player.getAbilities().flying) {
				player.getAbilities().flying = true;
				send = true;
			}
			if (send) {
				player.sendAbilitiesUpdate();
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		player.interactionManager.getGameMode().setAbilities(player.getAbilities());
		player.sendAbilitiesUpdate();
	}

	@Override
	public Target getStart() {
		return startPos;
	}

	@Override
	public Target getEnd() {
		return config.to();
	}

	@Override
	public void setProgress(long time) {
		this.progress = time;
	}

	@Override
	public long getProgress() {
		return progress;
	}

	@Override
	public void tickDelta(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, float delta) {
		if (prevStart == null) {
			prevStart = cutscene.getTransitions().getValuesAt(interval.getStart()-1).filter(
					movement -> movement instanceof SmoothMovementTransition
			).map(movement -> (SmoothMovementTransition) movement).findAny().map(
					SmoothMovementTransition::getStart
			).orElse(startPos);
		}
		if (nextEnd == null) {
			nextEnd = cutscene.getTransitions().getValuesAt(interval.getEnd()+1).filter(
					movement -> movement instanceof SmoothMovementTransition
			).map(movement -> (SmoothMovementTransition) movement).findAny().map(
					SmoothMovementTransition::getEnd
			).orElse(config.to());
		}

		var target = interpolate(delta, new Target[]{prevStart, startPos, config.to(), nextEnd});

		cutscene.forAllPlayers(player -> {
			player.networkHandler.requestTeleport(
					target.pos().x, target.pos().y, target.pos().z, target.yaw(), target.pitch()
			);
		});
	}

	@Override
	public void setTask(TimerTask task) {
		this.task = task;
	}

	@Override
	public TimerTask getTask() {
		return task;
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.MOVING_TRANSITION;
	}
}
