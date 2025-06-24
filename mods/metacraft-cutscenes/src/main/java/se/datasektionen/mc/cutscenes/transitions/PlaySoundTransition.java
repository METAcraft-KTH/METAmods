package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.position_ref.PositionRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.OptionalLong;

public class PlaySoundTransition implements Transition, TransitionConfig {

	public static final MapCodec<PlaySoundTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SoundEvent.ENTRY_CODEC.fieldOf("sound").forGetter(t -> t.sound),
					ExtraCodecs.SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundCategory.MASTER).forGetter(t -> t.category),
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

	private final RegistryEntry<SoundEvent> sound;
	private final SoundCategory category;
	private final Either<PositionRef, EntityRef> source;
	private final float volume;
	private final float pitch;
	private final OptionalLong seed;
	private final boolean stopAtEnd;

	public PlaySoundTransition(
			RegistryEntry<SoundEvent> sound, SoundCategory category, Either<PositionRef, EntityRef> source,
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
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		source.ifLeft(pos -> {
			pos.get(cutscene.createRefContext(player)).ifPresent(target -> {
				player.networkHandler.sendPacket(
						new PlaySoundS2CPacket(
								sound, category, target.getX(), target.getY(), target.getZ(),
								volume, pitch, seed.orElse(player.getRandom().nextLong())
						)
				);
			});
		});
		source.ifRight(entity -> {
			entity.get(cutscene.createRefContext(player)).forEach(target -> {
				player.networkHandler.sendPacket(
						new PlaySoundFromEntityS2CPacket(
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
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (stopAtEnd) {
			player.networkHandler.sendPacket(new StopSoundS2CPacket(sound.value().id(), category));
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
