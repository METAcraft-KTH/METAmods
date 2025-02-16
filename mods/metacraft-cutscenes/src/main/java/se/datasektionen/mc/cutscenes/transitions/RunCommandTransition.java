package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
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
					Codec.BOOL.optionalFieldOf("run_per_player", false).forGetter(t -> t.runPerPlayer)
			).apply(instance, RunCommandTransition::new)
	);

	private final Optional<String> activation;
	private final Optional<String> tickCommand;
	private final Optional<String> deactivation;
	private final boolean runInRealWorld;
	private final boolean runPerPlayer;

	public RunCommandTransition(Optional<String> activation, Optional<String> tickCommand, Optional<String> deactivation, boolean runInRealWorld, boolean runPerPlayer) {
		this.activation = activation;
		this.tickCommand = tickCommand;
		this.deactivation = deactivation;
		this.runInRealWorld = runInRealWorld;
		this.runPerPlayer = runPerPlayer;
	}

	public static ServerCommandSource getSource(ServerPlayerEntity player, CutsceneInstance cutscene, boolean runInRealWorld) {
		var source = player.getCommandSource().withMaxLevel(2);
		if (!runInRealWorld) {
			source = source.withWorld(cutscene.getCutsceneWorld());
		}
		return source.withSilent();
	}

	public static ServerCommandSource getSource(CutsceneInstance cutscene, boolean runInRealWorld) {
		return new ServerCommandSource(
				CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO,
				runInRealWorld ? cutscene.getCutsceneWorld().getActualWorld() : cutscene.getCutsceneWorld(),
				2, "Cutscene", Text.literal("Cutscene"), cutscene.getServer(), null
		).withSilent();
	}

	private void execute(CutsceneInstance cutscene, String command) {
		if (runPerPlayer) {
			cutscene.forAllPlayers(player -> {
				player.getServer().getCommandManager().executeWithPrefix(
						getSource(player, cutscene, runInRealWorld), command
				);
			});
		} else {
			cutscene.getServer().getCommandManager().executeWithPrefix(
					getSource(cutscene, runInRealWorld), command
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
