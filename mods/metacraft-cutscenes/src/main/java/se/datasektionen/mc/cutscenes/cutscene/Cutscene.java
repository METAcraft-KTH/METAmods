package se.datasektionen.mc.cutscenes.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
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
					TeleportTransition.SerializableTeleportTarget.TELEPORT_TARGET_CODEC.codec().optionalFieldOf("entry_point").forGetter(t -> t.entryPoint)
			).apply(instance, Cutscene::new)
	);

	private final IntervalMap<TransitionConfig> transitions;
	private boolean createFakePlayer;
	private boolean returnPlayerToStartPos;
	private boolean hideMount;
	private boolean resetPlayerData;
	private boolean hidePlayer;
	private final Optional<TeleportTransition.SerializableTeleportTarget> entryPoint;

	public Cutscene() {
		this(
				new IntervalMap<>(), false, true,
				true, true, true, Optional.empty()
		);
	}

	public Cutscene(
			IntervalMap<TransitionConfig> transitions,
			boolean createFakePlayer, boolean returnPlayerToStartPos, boolean hideMount,
			boolean resetPlayerData, boolean hidePlayer,
			Optional<TeleportTransition.SerializableTeleportTarget> entryPoint
	) {
		this.transitions = transitions;
		this.createFakePlayer = createFakePlayer;
		this.returnPlayerToStartPos = returnPlayerToStartPos;
		this.hideMount = hideMount;
		this.resetPlayerData = resetPlayerData;
		this.hidePlayer = hidePlayer;
		this.entryPoint = entryPoint;
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

	public Optional<TeleportTarget> getEntryPoint(MinecraftServer server, RegistryKey<World> cutsceneDim) {
		return entryPoint.flatMap(target -> target.getTeleportTarget(server, cutsceneDim));
	}
}
