package nu.metacraft.cutscenes.compat.resource_pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.resource_packs.ResourcePackHelper;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;

public class SetResourcePackTransition implements Transition {

	public static final MapCodec<SetResourcePackTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.BOOL.fieldOf("toggled").forGetter(t -> t.toggled)
			).apply(instance, SetResourcePackTransition::new)
	);

	private final Config config;
	private boolean toggled = false;

	public SetResourcePackTransition(Config config) {
		this.config = config;
	}

	public SetResourcePackTransition(Config config, boolean toggled) {
		this.config = config;
		this.toggled = toggled;
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
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!ResourcePackHelper.hasResourcePack(player, config.resourcePack)) {
			ResourcePackHelper.enableResourcePack(player, config.resourcePack, false);
			toggled = true;
		}
	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.disableAfterwards && toggled) {
			ResourcePackHelper.disableResourcePack(player, config.resourcePack);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return ResourcePackTransition.RESOURCE_PACK;
	}

	public record Config(UUID resourcePack, boolean disableAfterwards) implements TransitionConfig {
		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						UUIDUtil.STRING_CODEC.fieldOf("resource_pack").forGetter(t -> t.resourcePack),
						Codec.BOOL.optionalFieldOf("disable_afterwards", true).forGetter(t -> t.disableAfterwards)
				).apply(instance, Config::new)
		);

		@Override
		public Transition create() {
			return new SetResourcePackTransition(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return ResourcePackTransition.RESOURCE_PACK_CONFIG;
		}
	}
}
