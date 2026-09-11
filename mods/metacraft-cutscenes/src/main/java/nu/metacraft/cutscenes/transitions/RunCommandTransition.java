package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

import java.util.Optional;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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

	public static CommandSourceStack getSource(
			CutsceneInstance cutscene, boolean runInRealWorld, @Nullable Entity entity, boolean debug
	) {
		var entryPoint = entity == null ? cutscene.getCutscene().getEntryPoint(null, cutscene) : Optional.<TeleportTransition>empty();
		var source = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				if (debug) {
					if (entity instanceof ServerPlayer p) {
						p.sendSystemMessage(message);
					} else {
						cutscene.getPlayers().forEach(p -> p.sendSystemMessage(message));
					}
				}
			}

			@Override
			public boolean acceptsSuccess() {
				return debug;
			}

			@Override
			public boolean acceptsFailure() {
				return debug;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack stack;
		if (entity != null) {
			stack = new CommandSourceStack(
					source, entity.position(), entity.getRotationVector(),
					runInRealWorld ? cutscene.getCutsceneWorld().getActualWorld() : cutscene.getCutsceneWorld(),
					LevelBasedPermissionSet.GAMEMASTER,
					cutscene.getServer(), entity
			);
		} else {
			stack = new CommandSourceStack(
					source,
					entryPoint.map(TeleportTransition::position).orElse(Vec3.ZERO),
					entryPoint.map(target -> new Vec2(target.xRot(), target.yRot())).orElse(Vec2.ZERO),
					runInRealWorld ? cutscene.getCutsceneWorld().getActualWorld() : cutscene.getCutsceneWorld(),
					LevelBasedPermissionSet.GAMEMASTER, Component.literal("Cutscene"),
					cutscene.getServer()
			);
		}

		if (!debug) {
			return stack.withSuppressedOutput();
		}
		return stack;
	}

	private static void execute(Commands manager, CommandSourceStack source, String command, boolean debug) {
		manager.performPrefixedCommand(source, command);
	}

	private void execute(CutsceneInstance cutscene, String command) {
		if (runPerPlayer) {
			cutscene.forAllPlayers(player -> {
				execute(
						player.level().getServer().getCommands(),
						getSource(cutscene, runInRealWorld, player, debug), command, debug
				);
			});
		} else {
			execute(
					cutscene.getServer().getCommands(),
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
