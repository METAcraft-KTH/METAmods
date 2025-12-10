package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.extension.ServerPlayerExtensions;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.util.Target;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.MovingTransitionConfig;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TimerTask;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

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
					cutscene.getPlayers().stream().findAny().map(Target::fromEntity).orElse(
							new Target(Vec3.ZERO, 0, 0)
					)
			);
		}
	}

	public static double interpolate(double x, double[] p) {
		//https://www.paulinternet.nl/?page=bicubic
		return p[1] + 0.5 * x*(p[2] - p[0] + x*(2.0*p[0] - 5.0*p[1] + 4.0*p[2] - p[3] + x*(3.0*(p[1] - p[2]) + p[3] - p[0])));
	}

	public static Vec3 interpolate(double x, Vec3[] p) {
		return new Vec3(
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3::x).toArray()),
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3::y).toArray()),
				interpolate(x, Arrays.stream(p).mapToDouble(Vec3::z).toArray())
		);
	}

	public static Target interpolate(double x, Target[] p) {
		return new Target(
				interpolate(x, Arrays.stream(p).map(Target::pos).toArray(Vec3[]::new)),
				(float) interpolate(x, Arrays.stream(p).mapToDouble(Target::yaw).toArray()),
				(float) interpolate(x, Arrays.stream(p).mapToDouble(Target::pitch).toArray())
		);
	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		((ServerPlayerExtensions) player).metacraft$setAllowWrongMovements(true);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			boolean send = false;
			if (!player.getAbilities().mayfly) {
				player.getAbilities().mayfly = true;
				send = true;
			}
			if (!player.getAbilities().flying) {
				player.getAbilities().flying = true;
				send = true;
			}
			if (send) {
				player.onUpdateAbilities();
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
		player.onUpdateAbilities();
		if (cutscene.getTransitions().getValuesAt(interval.getEnd()+1).noneMatch(
				movement -> movement instanceof MovingTransition
		)) {
			((ServerPlayerExtensions) player).metacraft$setAllowWrongMovements(false);
		}
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

	private int prevTick = 0;

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
			//We don't use requestTeleport because it's not thread safe.
			player.connection.send(new ClientboundPlayerPositionPacket(
					-1, //Ignores the teleport confirm packet.
					new PositionMoveRotation(target.pos(), Vec3.ZERO, target.yaw(), target.pitch()),
					Set.of()
			));
			if (prevTick != cutscene.getCurrentTime()) {
				player.level().getServer().execute(() -> {
					player.absSnapTo(
							target.pos().x, target.pos().y, target.pos().z, target.yaw(), target.pitch()
					);
				});
			}
		});
		prevTick = cutscene.getCurrentTime();
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
