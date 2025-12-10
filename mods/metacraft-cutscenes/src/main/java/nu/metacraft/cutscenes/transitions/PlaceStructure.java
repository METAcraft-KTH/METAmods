package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

public class PlaceStructure extends InstantTransition {

	public static final MapCodec<PlaceStructure> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ParsedStructure.CODEC.fieldOf("structure").forGetter(t -> t.structure),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					PositionRefRegistry.CODEC.optionalFieldOf("pivot").forGetter(t -> t.pivot),
					BlockPos.CODEC.optionalFieldOf("local_pivot", BlockPos.ZERO).forGetter(t -> t.localPivot),
					Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(t -> t.mirror),
					Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(t -> t.rotation),
					Codec.BOOL.optionalFieldOf("ignore_entities", false).forGetter(t -> t.ignoreEntities),
					LiquidSettings.CODEC.optionalFieldOf(
							"structure_liquid_settings", LiquidSettings.IGNORE_WATERLOGGING
					).forGetter(t -> t.structureLiquidSettings),
					Codec.BOOL.optionalFieldOf("update_neighbours", true).forGetter(t -> t.updateNeighbours),
					Codec.BOOL.optionalFieldOf("force_state", false).forGetter(t -> t.forceState),
					Codec.BOOL.optionalFieldOf("skip_drops", true).forGetter(t -> t.skipDrops),
					StructureProcessorType.LIST_CODEC.optionalFieldOf(
							"processors", Holder.direct(new StructureProcessorList(List.of()))
					).forGetter(t -> t.processors),
					Codec.LONG.optionalFieldOf("seed").forGetter(t -> t.seed)
			).apply(instance, PlaceStructure::new)
	);

	private final ParsedStructure structure;
	private final PositionRef pos;
	private final Optional<PositionRef> pivot;
	private final BlockPos localPivot;
	private final Mirror mirror;
	private final Rotation rotation;
	private final boolean ignoreEntities;
	private final LiquidSettings structureLiquidSettings;
	private final boolean updateNeighbours;
	private final boolean forceState;
	private final boolean skipDrops;
	private final Holder<StructureProcessorList> processors;
	private final Optional<Long> seed;

	private PlaceStructure(
			ParsedStructure structure, PositionRef pos, Optional<PositionRef> pivot,
			BlockPos localPivot,
			Mirror mirror, Rotation rotation, boolean ignoreEntities,
			LiquidSettings structureLiquidSettings, boolean updateNeighbours,
			boolean forceState, boolean skipDrops, Holder<StructureProcessorList> processors,
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
		structure.get(cutscene.getServer().getStructureManager(), cutscene.getServer().registryAccess()).ifPresent(structure -> {
			this.pos.get(cutscene.getRefContext()).ifPresent(exactPos -> {
				var pos = BlockPos.containing(exactPos);
				var random = seed.map(RandomSource::create).orElse(cutscene.getRandom());
				var placementData = new StructurePlaceSettings()
						.setRotationPivot(localPivot)
						.setMirror(mirror)
						.setRotation(rotation)
						.setIgnoreEntities(ignoreEntities)
						.setRandom(random)
						.setLiquidSettings(structureLiquidSettings)
						.setKnownShape(!updateNeighbours); //Fabric Yarn uses a misleading name here, updateNeighbours should be false to perform a neighbour update.
				for (var processor : processors.value().list()) {
					placementData.addProcessor(processor);
				}

				int flags =
						Block.UPDATE_CLIENTS |
								(forceState ? Block.UPDATE_KNOWN_SHAPE : 0) |
								(skipDrops ? Block.UPDATE_SUPPRESS_DROPS : 0);

				var pivot = BlockPos.containing(this.pivot.flatMap(p -> p.get(cutscene.getRefContext())).orElse(exactPos));
				structure.placeInWorld(
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
