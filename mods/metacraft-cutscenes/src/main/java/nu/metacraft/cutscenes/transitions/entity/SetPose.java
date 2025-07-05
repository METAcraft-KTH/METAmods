package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Uuids;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.core.entity.PoseLockable;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SetPose implements Transition {

	public static final MapCodec<SetPose> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					ExtraCodecs.createListSerializedMap(
							Uuids.STRICT_CODEC.fieldOf("uuid"),
							ExtraCodecs.ENTITY_POSE_CODEC.fieldOf("pose"),
							HashMap::new
					).fieldOf("prev_pose").forGetter(t -> t.prevPose)
			).apply(instance, SetPose::new)
	);

	protected final Config config;
	protected final Map<UUID, EntityPose> prevPose;

	public SetPose(Config config) {
		this.config = config;
		this.prevPose = new HashMap<>();
	}

	public SetPose(Config config, Map<UUID, EntityPose> prevPose) {
		this.config = config;
		this.prevPose = prevPose;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity.get(cutscene.getRefContext()).forEach(entity -> {
			if (entity.getPose() != config.pose) {
				if (!prevPose.containsKey(entity.getUuid())) {
					prevPose.put(entity.getUuid(), entity.getPose());
				}
				if (entity instanceof PoseLockable lockable) {
					lockable.setLockPose(true);
				}
				entity.setPose(config.pose);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity.get(cutscene.getRefContext()).forEach(entity -> {
			if (prevPose.containsKey(entity.getUuid())) {
				entity.setPose(prevPose.get(entity.getUuid()));
			}
			if (entity instanceof PoseLockable lockable) {
				lockable.setLockPose(false);
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_SET_POSE;
	}

	public record Config(EntityRef entity, EntityPose pose) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(Config::entity),
						ExtraCodecs.ENTITY_POSE_CODEC.fieldOf("pose").forGetter(Config::pose)
				).apply(instance, Config::new)
		);

		@Override
		public Transition create() {
			return new SetPose(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.ENTITY_SET_POSE;
		}
	}
}
