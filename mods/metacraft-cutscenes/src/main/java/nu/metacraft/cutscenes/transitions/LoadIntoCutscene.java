package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.ChunkAreaRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

public class LoadIntoCutscene extends InstantTransition {

	protected final ChunkAreaRegistry.ChunkArea chunks;

	public static final MapCodec<LoadIntoCutscene> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ChunkAreaRegistry.CODEC.fieldOf("chunks").forGetter(t -> t.chunks)
			).apply(instance, LoadIntoCutscene::new)
	);

	public LoadIntoCutscene(ChunkAreaRegistry.ChunkArea chunks) {
		this.chunks = chunks;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		chunks.getPositions(cutscene.getCutsceneWorld().getActualWorld()).forEach(chunk -> {
			cutscene.getCutsceneWorld().getChunk(chunk.x(), chunk.z());
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.LOAD_CHUNKS;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.LOAD_CHUNKS;
	}
}
