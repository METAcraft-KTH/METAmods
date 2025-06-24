package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.position_ref.PositionRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

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
			entity.sleep(pos);
		});
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		pos.get(cutscene.getRefContext()).ifPresent(foundPos -> {
			var pos = BlockPos.ofFloored(foundPos);
			var bedState = cutscene.getCutsceneWorld().getBlockState(pos);
			if (!(bedState.getBlock() instanceof BedBlock)) {
				return;
			}
			if (bedState.get(BedBlock.PART) == BedPart.HEAD) {
				sleepAt(cutscene, pos);
			} else {
				pos = pos.offset(bedState.get(BedBlock.FACING));
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
				entity.wakeUp();
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
