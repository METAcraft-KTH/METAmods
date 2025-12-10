package nu.metacraft.cutscenes.transitions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.lib.util.METACodecs;

import java.util.OptionalLong;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class PlaySoundTransition implements Transition, TransitionConfig {

	public static final MapCodec<PlaySoundTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SoundEvent.CODEC.fieldOf("sound").forGetter(t -> t.sound),
					METACodecs.SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundSource.MASTER).forGetter(t -> t.category),
					Codec.either(PositionRefRegistry.CODEC, EntityRefRegistry.CODEC).fieldOf("source").forGetter(t -> t.source),
					Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("volume", 1.0f).forGetter(t -> t.volume),
					Codec.floatRange(0.5f, 2).optionalFieldOf("pitch", 1.0f).forGetter(t -> t.pitch),
					Codec.LONG.optionalFieldOf("seed").xmap(
							opt -> opt.stream().mapToLong(u -> u).findAny(),
							opt -> opt.stream().boxed().findAny()
					).forGetter(t -> t.seed),
					Codec.BOOL.optionalFieldOf("stop_at_end", true).forGetter(t -> t.stopAtEnd)
			).apply(instance, PlaySoundTransition::new)
	);

	private final Holder<SoundEvent> sound;
	private final SoundSource category;
	private final Either<PositionRef, EntityRef> source;
	private final float volume;
	private final float pitch;
	private final OptionalLong seed;
	private final boolean stopAtEnd;

	public PlaySoundTransition(
			Holder<SoundEvent> sound, SoundSource category, Either<PositionRef, EntityRef> source,
			float volume, float pitch, OptionalLong seed, boolean stopAtEnd
	) {
		this.sound = sound;
		this.category = category;
		this.source = source;
		this.volume = volume;
		this.pitch = pitch;
		this.seed = seed;
		this.stopAtEnd = stopAtEnd;
	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		source.ifLeft(pos -> {
			pos.get(cutscene.createRefContext(player)).ifPresent(target -> {
				player.connection.send(
						new ClientboundSoundPacket(
								sound, category, target.x(), target.y(), target.z(),
								volume, pitch, seed.orElse(player.getRandom().nextLong())
						)
				);
			});
		});
		source.ifRight(entity -> {
			entity.get(cutscene.createRefContext(player)).forEach(target -> {
				player.connection.send(
						new ClientboundSoundEntityPacket(
								sound, category, target,
								volume, pitch, seed.orElse(player.getRandom().nextLong())
						)
				);
			});
		});
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (stopAtEnd) {
			player.connection.send(new ClientboundStopSoundPacket(sound.value().location(), category));
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SOUND;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SOUND;
	}
}
