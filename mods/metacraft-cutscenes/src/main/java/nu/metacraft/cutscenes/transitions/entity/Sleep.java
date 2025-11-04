package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

public class Sleep implements Transition, TransitionConfig {

	public static final MapCodec<Sleep> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos)
			).apply(instance, Sleep::new)
	);

	private final EntityRef entity;
	private final PositionRef pos;

	public Sleep(EntityRef entity, PositionRef pos) {
		this.entity = entity;
		this.pos = pos;
	}

	private void sleepAt(CutsceneInstance cutscene, BlockPos pos) {
		entity.get(cutscene.getRefContext()).filter(
				entity -> entity instanceof LivingEntity
		).map(entity -> (LivingEntity) entity).findAny().ifPresent(entity -> {
			entity.startSleeping(pos);
		});
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		pos.get(cutscene.getRefContext()).ifPresent(foundPos -> {
			var pos = BlockPos.containing(foundPos);
			var bedState = cutscene.getCutsceneWorld().getBlockState(pos);
			if (!(bedState.getBlock() instanceof BedBlock)) {
				return;
			}
			if (bedState.getValue(BedBlock.PART) == BedPart.HEAD) {
				sleepAt(cutscene, pos);
			} else {
				pos = pos.relative(bedState.getValue(BedBlock.FACING));
				if (cutscene.getCutsceneWorld().getBlockState(pos).getBlock() instanceof BedBlock) {
					sleepAt(cutscene, pos);
				}
			}
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).filter(
				entity -> entity instanceof LivingEntity
		).map(entity -> (LivingEntity) entity).findAny().ifPresent(entity -> {
			if (entity.isSleeping()) {
				entity.stopSleeping();
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SLEEP;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SLEEP;
	}
}
