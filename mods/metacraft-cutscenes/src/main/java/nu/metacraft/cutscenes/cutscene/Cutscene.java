package nu.metacraft.cutscenes.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import nu.metacraft.cutscenes.CutscenesConfig;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.TeleportTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;

import java.util.Optional;

public class Cutscene {

	public static MapCodec<Cutscene> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					IntervalMap.createCodec(TransitionConfigRegistry.CODEC.fieldOf("config")).fieldOf("transitions").forGetter(c -> c.transitions),
					Codec.BOOL.fieldOf("return_player_to_start").forGetter(a -> a.returnPlayerToStartPos),
					Codec.BOOL.optionalFieldOf("reset_player_data", true).forGetter(a -> a.resetPlayerData),
					Codec.BOOL.optionalFieldOf("hide_player", true).forGetter(a -> a.hidePlayer),
					Codec.BOOL.optionalFieldOf("skippable", true).forGetter(a -> a.skippable),
					Codec.BOOL.optionalFieldOf("resend_chunks_before_next_cutscene", false).forGetter(a -> a.resendChunksBeforeNextCutscene),
					ScoreboardMode.CODEC.optionalFieldOf("scoreboard", ScoreboardMode.SYNC).forGetter(a -> a.scoreboardMode),
					Codec.STRING.optionalFieldOf("finish_command").forGetter(t -> t.finishCommand),
					TeleportTransition.SerializableTeleportTargetBoth.CODEC.optionalFieldOf("entry_point").forGetter(t -> t.entryPoint),
					TeleportTransition.SerializableTeleportTargetBoth.CODEC.optionalFieldOf("exit_point").forGetter(t -> t.exitPoint),
					Codec.STRING.optionalFieldOf("next_cutscene").forGetter(t -> t.nextCutscene)
			).apply(instance, Cutscene::new)
	);

	private final IntervalMap<TransitionConfig> transitions;
	private boolean returnPlayerToStartPos;
	private boolean resetPlayerData;
	private boolean hidePlayer;
	private boolean skippable;
	private boolean resendChunksBeforeNextCutscene;
	private ScoreboardMode scoreboardMode;
	private final Optional<String> finishCommand;
	private final Optional<TeleportTransition.SerializableTeleportTargetBoth> entryPoint;
	private final Optional<TeleportTransition.SerializableTeleportTargetBoth> exitPoint;
	private final Optional<String> nextCutscene;

	private Optional<Cutscene> cachedNextCutscene = Optional.empty();

	public Cutscene() {
		this(new IntervalMap<>());
	}

	public Cutscene(IntervalMap<TransitionConfig> transitions) {
		this(
				transitions,
				true, true, true, true,
				false, ScoreboardMode.SYNC, Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty()
		);
	}

	public Cutscene(
			IntervalMap<TransitionConfig> transitions,
			boolean returnPlayerToStartPos,
			boolean resetPlayerData, boolean hidePlayer, boolean skippable, boolean resendChunksBeforeNextCutscene,
			ScoreboardMode scoreboard,
			Optional<String> finishCommand,
			Optional<TeleportTransition.SerializableTeleportTargetBoth> entryPoint,
			Optional<TeleportTransition.SerializableTeleportTargetBoth> exitPoint,
			Optional<String> nextCutscene
	) {
		this.transitions = transitions;
		this.returnPlayerToStartPos = returnPlayerToStartPos;
		this.resetPlayerData = resetPlayerData;
		this.hidePlayer = hidePlayer;
		this.skippable = skippable;
		this.resendChunksBeforeNextCutscene = resendChunksBeforeNextCutscene;
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
		this.nextCutscene = nextCutscene;
		this.scoreboardMode = scoreboard;
		this.finishCommand = finishCommand;
	}

	public IntervalMap<Transition> createTransitions() {
		return new IntervalMap<>(transitions.getIntervals().stream().map(object -> object.map(TransitionConfig::create)));
	}

	public boolean returnToStart() {
		return returnPlayerToStartPos;
	}

	public boolean resetPlayerData() {
		return resetPlayerData;
	}

	public boolean hidePlayer() {
		return hidePlayer;
	}

	public boolean isSkippable() {
		return skippable;
	}

	public boolean shouldResendChunksBeforeNextCutscene() {
		return resendChunksBeforeNextCutscene;
	}

	public ScoreboardMode getScoreboardMode() {
		return scoreboardMode;
	}

	public Optional<Cutscene> getNextCutscene(MinecraftServer server) {
		if (cachedNextCutscene.isEmpty() && nextCutscene.isPresent()) {
			cachedNextCutscene = nextCutscene.flatMap(CutscenesConfig.getOrCreateConfig(server)::getCutscene);
		}
		return cachedNextCutscene;
	}

	public Optional<String> getFinishCommand() {
		return finishCommand;
	}

	public Optional<RegistryKey<World>> getEntryDim() {
		return entryPoint.flatMap(
				TeleportTransition.SerializableTeleportTargetBoth::getDim
		);
	}

	public Optional<TeleportTarget> getEntryPoint(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
		return entryPoint.flatMap(target -> target.getTeleportTarget(player, cutscene));
	}

	public boolean hasExitPoint() {
		return exitPoint.isPresent();
	}

	public Optional<TeleportTarget> getExitPoint(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
		return exitPoint.flatMap(target -> target.getTeleportTarget(player, cutscene));
	}

	public enum ScoreboardMode implements StringIdentifiable {
		SYNC("sync"),
		COPY("copy"),
		EMPTY("empty");

		public static final Codec<ScoreboardMode> CODEC = StringIdentifiable.createCodec(ScoreboardMode::values);

		private final String name;

		ScoreboardMode(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	@VisibleForTesting
	public Cutscene setCachedNextCutscene(Cutscene cachedNextCutscene) {
		this.cachedNextCutscene = Optional.of(cachedNextCutscene);
		return this;
	}

	@VisibleForTesting
	public Cutscene setReturnPlayerToStartPos(boolean returnPlayerToStartPos) {
		this.returnPlayerToStartPos = returnPlayerToStartPos;
		return this;
	}

	@VisibleForTesting
	public Cutscene resetPlayerData(boolean resetPlayerData) {
		this.resetPlayerData = resetPlayerData;
		return this;
	}
}
