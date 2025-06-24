package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

import java.util.Optional;

public class RunCommandTransition implements Transition, TransitionConfig {

	public static final MapCodec<RunCommandTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.optionalFieldOf("activation").forGetter(t -> t.activation),
					Codec.STRING.optionalFieldOf("tick_command").forGetter(t -> t.tickCommand),
					Codec.STRING.optionalFieldOf("deactivation").forGetter(t -> t.deactivation),
					Codec.BOOL.optionalFieldOf("run_in_real_world", false).forGetter(t -> t.runInRealWorld),
					Codec.BOOL.optionalFieldOf("run_per_player", false).forGetter(t -> t.runPerPlayer),
					Codec.BOOL.optionalFieldOf("debug", false).forGetter(t -> t.debug)
			).apply(instance, RunCommandTransition::new)
	);

	private final Optional<String> activation;
	private final Optional<String> tickCommand;
	private final Optional<String> deactivation;
	private final boolean runInRealWorld;
	private final boolean runPerPlayer;
	private final boolean debug;

	public RunCommandTransition(
			Optional<String> activation, Optional<String> tickCommand, Optional<String> deactivation,
			boolean runInRealWorld, boolean runPerPlayer, boolean debug
	) {
		this.activation = activation;
		this.tickCommand = tickCommand;
		this.deactivation = deactivation;
		this.runInRealWorld = runInRealWorld;
		this.runPerPlayer = runPerPlayer;
		this.debug = debug;
	}

	public static ServerCommandSource getSource(
			CutsceneInstance cutscene, boolean runInRealWorld, @Nullable Entity entity, boolean debug
	) {
		var entryPoint = entity == null ? cutscene.getCutscene().getEntryPoint(null, cutscene) : Optional.<TeleportTarget>empty();
		var src = new ServerCommandSource(
				new CommandOutput() {
					@Override
					public void sendMessage(Text message) {
						if (debug) {
							if (entity instanceof ServerPlayerEntity p) {
								p.sendMessage(message);
							} else {
								cutscene.getPlayers().forEach(p -> p.sendMessage(message));
							}
						}
					}

					@Override
					public boolean shouldReceiveFeedback() {
						return debug;
					}

					@Override
					public boolean shouldTrackOutput() {
						return debug;
					}

					@Override
					public boolean shouldBroadcastConsoleToOps() {
						return false;
					}
				},
				entity != null ? entity.getPos() : entryPoint.map(TeleportTarget::position).orElse(Vec3d.ZERO),
				entity != null ? entity.getRotationClient() : entryPoint.map(target -> new Vec2f(target.pitch(), target.yaw())).orElse(Vec2f.ZERO),
				runInRealWorld ? cutscene.getCutsceneWorld().getActualWorld() : cutscene.getCutsceneWorld(),
				2, entity != null ? entity.getName().getString() : "Cutscene",
				entity != null ? entity.getDisplayName() : Text.literal("Cutscene"),
				cutscene.getServer(), entity
		);
		if (!debug) {
			return src.withSilent();
		}
		return src;
	}

	private static void execute(CommandManager manager, ServerCommandSource source, String command, boolean debug) {
		manager.executeWithPrefix(source, command);
	}

	private void execute(CutsceneInstance cutscene, String command) {
		if (runPerPlayer) {
			cutscene.forAllPlayers(player -> {
				execute(
						player.getServer().getCommandManager(),
						getSource(cutscene, runInRealWorld, player, debug), command, debug
				);
			});
		} else {
			execute(
					cutscene.getServer().getCommandManager(),
					getSource(cutscene, runInRealWorld, null, debug), command, debug
			);
		}
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		activation.ifPresent(command -> execute(cutscene, command));
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		tickCommand.ifPresent(command -> execute(cutscene, command));
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		deactivation.ifPresent(command -> execute(cutscene, command));
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.COMMAND;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.COMMAND;
	}
}
