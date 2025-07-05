package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.util.ParsedStructure;

import java.util.List;
import java.util.Optional;

public class PlaceStructure extends InstantTransition {

	public static final MapCodec<PlaceStructure> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ParsedStructure.CODEC.fieldOf("structure").forGetter(t -> t.structure),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					PositionRefRegistry.CODEC.optionalFieldOf("pivot").forGetter(t -> t.pivot),
					BlockPos.CODEC.optionalFieldOf("local_pivot", BlockPos.ORIGIN).forGetter(t -> t.localPivot),
					BlockMirror.CODEC.optionalFieldOf("mirror", BlockMirror.NONE).forGetter(t -> t.mirror),
					BlockRotation.CODEC.optionalFieldOf("rotation", BlockRotation.NONE).forGetter(t -> t.rotation),
					Codec.BOOL.optionalFieldOf("ignore_entities", false).forGetter(t -> t.ignoreEntities),
					StructureLiquidSettings.codec.optionalFieldOf(
							"structure_liquid_settings", StructureLiquidSettings.IGNORE_WATERLOGGING
					).forGetter(t -> t.structureLiquidSettings),
					Codec.BOOL.optionalFieldOf("update_neighbours", true).forGetter(t -> t.updateNeighbours),
					Codec.BOOL.optionalFieldOf("force_state", false).forGetter(t -> t.forceState),
					Codec.BOOL.optionalFieldOf("skip_drops", true).forGetter(t -> t.skipDrops),
					StructureProcessorType.REGISTRY_CODEC.optionalFieldOf(
							"processors", RegistryEntry.of(new StructureProcessorList(List.of()))
					).forGetter(t -> t.processors),
					Codec.LONG.optionalFieldOf("seed").forGetter(t -> t.seed)
			).apply(instance, PlaceStructure::new)
	);

	private final ParsedStructure structure;
	private final PositionRef pos;
	private final Optional<PositionRef> pivot;
	private final BlockPos localPivot;
	private final BlockMirror mirror;
	private final BlockRotation rotation;
	private final boolean ignoreEntities;
	private final StructureLiquidSettings structureLiquidSettings;
	private final boolean updateNeighbours;
	private final boolean forceState;
	private final boolean skipDrops;
	private final RegistryEntry<StructureProcessorList> processors;
	private Optional<Long> seed;

	private PlaceStructure(
			ParsedStructure structure, PositionRef pos, Optional<PositionRef> pivot,
			BlockPos localPivot,
			BlockMirror mirror, BlockRotation rotation, boolean ignoreEntities,
			StructureLiquidSettings structureLiquidSettings, boolean updateNeighbours,
			boolean forceState, boolean skipDrops, RegistryEntry<StructureProcessorList> processors,
			Optional<Long> seed
	) {
		this.structure = structure;
		this.pos = pos;
		this.pivot = pivot;
		this.localPivot = localPivot;
		this.mirror = mirror;
		this.rotation = rotation;
		this.ignoreEntities = ignoreEntities;
		this.structureLiquidSettings = structureLiquidSettings;
		this.updateNeighbours = updateNeighbours;
		this.forceState = forceState;
		this.skipDrops = skipDrops;
		this.processors = processors;
		this.seed = seed;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		structure.get(cutscene.getServer().getStructureTemplateManager(), cutscene.getServer().getRegistryManager()).ifPresent(structure -> {
			this.pos.get(cutscene.getRefContext()).ifPresent(exactPos -> {
				var pos = BlockPos.ofFloored(exactPos);
				var random = seed.map(Random::create).orElse(cutscene.getRandom());
				var placementData = new StructurePlacementData()
						.setPosition(localPivot)
						.setMirror(mirror)
						.setRotation(rotation)
						.setIgnoreEntities(ignoreEntities)
						.setRandom(random)
						.setLiquidSettings(structureLiquidSettings)
						.setUpdateNeighbors(!updateNeighbours); //Fabric Yarn uses a misleading name here, updateNeighbours should be false to perform a neighbour update.
				for (var processor : processors.value().getList()) {
					placementData.addProcessor(processor);
				}

				int flags =
						Block.NOTIFY_LISTENERS |
								(forceState ? Block.FORCE_STATE : 0) |
								(skipDrops ? Block.SKIP_DROPS : 0);

				var pivot = BlockPos.ofFloored(this.pivot.flatMap(p -> p.get(cutscene.getRefContext())).orElse(exactPos));
				structure.place(
						cutscene.getCutsceneWorld(), pos, pivot,
						placementData, random, flags
				);
			});
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.PLACE_STRUCTURE;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.PLACE_STRUCTURE;
	}
}
