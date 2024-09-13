package se.datasektionen.mc.cutscenes.compat.resource_pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.resource_packs.ResourcePackHelper;

import java.util.UUID;

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
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (!ResourcePackHelper.hasResourcePack(player, config.resourcePack)) {
			ResourcePackHelper.enableResourcePack(player, config.resourcePack);
			toggled = true;
		}
	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
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
						Uuids.STRING_CODEC.fieldOf("resource_pack").forGetter(t -> t.resourcePack),
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
