package se.datasektionen.mc.cutscenes.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.VisibleForTesting;
import se.datasektionen.mc.cutscenes.CutscenesConfig;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.TeleportTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;

import java.util.Optional;

public class Cutscene {

	public static MapCodec<Cutscene> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					IntervalMap.createCodec(TransitionConfigRegistry.CODEC.fieldOf("config")).fieldOf("transitions").forGetter(c -> c.transitions),
					Codec.BOOL.fieldOf("create_fake_player").forGetter(a -> a.createFakePlayer),
					Codec.BOOL.fieldOf("return_player_to_start").forGetter(a -> a.returnPlayerToStartPos),
					Codec.BOOL.fieldOf("hide_mount").forGetter(a -> a.hideMount),
					Codec.BOOL.optionalFieldOf("reset_player_data", true).forGetter(a -> a.resetPlayerData),
					Codec.BOOL.optionalFieldOf("hide_player", true).forGetter(a -> a.hidePlayer),
					Codec.BOOL.optionalFieldOf("skippable", true).forGetter(a -> a.skippable),
					Codec.BOOL.optionalFieldOf("resend_chunks_before_next_cutscene", false).forGetter(a -> a.resendChunksBeforeNextCutscene),
					ScoreboardMode.CODEC.optionalFieldOf("scoreboard", ScoreboardMode.SYNC).forGetter(a -> a.scoreboardMode),
					TeleportTransition.SerializableTeleportTarget.TELEPORT_TARGET_CODEC.codec().optionalFieldOf("entry_point").forGetter(t -> t.entryPoint),
					TeleportTransition.SerializableTeleportTarget.TELEPORT_TARGET_CODEC.codec().optionalFieldOf("exit_point").forGetter(t -> t.exitPoint),
					Codec.STRING.optionalFieldOf("next_cutscene").forGetter(t -> t.nextCutscene)
			).apply(instance, Cutscene::new)
	);

	private final IntervalMap<TransitionConfig> transitions;
	private boolean createFakePlayer;
	private boolean returnPlayerToStartPos;
	private boolean hideMount;
	private boolean resetPlayerData;
	private boolean hidePlayer;
	private boolean skippable;
	private boolean resendChunksBeforeNextCutscene;
	private ScoreboardMode scoreboardMode;
	private final Optional<TeleportTransition.SerializableTeleportTarget> entryPoint;
	private final Optional<TeleportTransition.SerializableTeleportTarget> exitPoint;
	private final Optional<String> nextCutscene;

	private Optional<Cutscene> cachedNextCutscene = Optional.empty();

	public Cutscene() {
		this(new IntervalMap<>());
	}

	public Cutscene(IntervalMap<TransitionConfig> transitions) {
		this(
				transitions, false, true,
				true, true, true, true,
				false, ScoreboardMode.SYNC,
				Optional.empty(), Optional.empty(), Optional.empty()
		);
	}

	public Cutscene(
			IntervalMap<TransitionConfig> transitions,
			boolean createFakePlayer, boolean returnPlayerToStartPos, boolean hideMount,
			boolean resetPlayerData, boolean hidePlayer, boolean skippable, boolean resendChunksBeforeNextCutscene,
			ScoreboardMode scoreboard,
			Optional<TeleportTransition.SerializableTeleportTarget> entryPoint,
			Optional<TeleportTransition.SerializableTeleportTarget> exitPoint,
			Optional<String> nextCutscene
	) {
		this.transitions = transitions;
		this.createFakePlayer = createFakePlayer;
		this.returnPlayerToStartPos = returnPlayerToStartPos;
		this.hideMount = hideMount;
		this.resetPlayerData = resetPlayerData;
		this.hidePlayer = hidePlayer;
		this.skippable = skippable;
		this.resendChunksBeforeNextCutscene = resendChunksBeforeNextCutscene;
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
		this.nextCutscene = nextCutscene;
		this.scoreboardMode = scoreboard;
	}

	public IntervalMap<Transition> createTransitions() {
		return new IntervalMap<>(transitions.getIntervals().stream().map(object -> object.map(TransitionConfig::create)));
	}

	public boolean returnToStart() {
		return returnPlayerToStartPos;
	}

	public boolean createFakePlayer() {
		return createFakePlayer;
	}

	public boolean hideMount() {
		return hideMount;
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

	public Optional<TeleportTarget> getEntryPoint(MinecraftServer server, RegistryKey<World> cutsceneDim) {
		return entryPoint.flatMap(target -> target.getTeleportTarget(server, cutsceneDim));
	}

	public boolean hasExitPoint() {
		return exitPoint.isPresent();
	}

	public Optional<TeleportTarget> getExitPoint(MinecraftServer server, RegistryKey<World> cutsceneDim) {
		return exitPoint.flatMap(target -> target.getTeleportTarget(server, cutsceneDim));
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
