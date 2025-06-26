package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.SpawnParticleTransitionConfig;

public class SpawnParticleTransition implements Transition {

	public static final MapCodec<SpawnParticleTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SpawnParticleTransitionConfig.CODEC.forGetter(t -> t.config),
					Codec.INT.fieldOf("start_tick").forGetter(t -> t.startTick)
			).apply(instance, SpawnParticleTransition::new)
	);

	private final SpawnParticleTransitionConfig config;
	private int startTick;

	public SpawnParticleTransition(SpawnParticleTransitionConfig config) {
		this.config = config;
	}
	public SpawnParticleTransition(SpawnParticleTransitionConfig config, int startTick) {
		this(config);
		this.startTick = startTick;
	}


	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		this.startTick = cutscene.getCurrentTime() % config.spawnInterval();
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.age % config.spawnInterval() == startTick) {
				config.pos().get(cutscene.createRefContext(player)).ifPresent(pos -> {
					var particle = config.particle().map(
							p -> p,
							p -> p.get(cutscene.getServer()).resultOrPartial(Cutscenes.LOGGER::error).orElse(null)
					);
					if (particle != null) {
						player.getWorld().spawnParticles(
								player, particle, config.force(), config.important(), pos.x, pos.y, pos.z,
								config.count(), config.delta().x, config.delta().y, config.delta().z, config.speed()
						);
					}
				});
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.PARTICLE;
	}
}
