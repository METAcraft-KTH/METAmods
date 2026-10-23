package se.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.position_ref.PositionRef;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.core.registry.PositionRefRegistry;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.transitions.TransitionType;
import se.metacraft.cutscenes.transitions.config.TransitionConfig;
import se.metacraft.cutscenes.transitions.config.TransitionConfigType;
import se.metacraft.cutscenes.util.IntervalMap;

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
