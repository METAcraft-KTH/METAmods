package nu.metacraft.cutscenes.transitions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.ResolutionContext;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

public class MessageTransition extends InstantTransition {

	public static final MapCodec<MessageTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("message").forGetter(t -> t.message),
					Codec.BOOL.optionalFieldOf("overlay", false).forGetter(t -> t.overlay)
			).apply(instance, MessageTransition::new)
	);

	private final Component message;
	private final boolean overlay;

	public MessageTransition(Component message, boolean overlay) {
		this.message = message;
		this.overlay = overlay;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		player.sendSystemMessage(parseText(player, cutscene, message), overlay);
	}

	public static Component parseText(ServerPlayer player, CutsceneInstance cutscene, Component text) {
		try {
			return ComponentUtils.resolve(
					ResolutionContext.builder().withSource(
							RunCommandTransition.getSource(cutscene, false, player, false)
					).withEntityOverride(
							player
					).build(), text
			);
		} catch (CommandSyntaxException e) {
			Cutscenes.LOGGER.error(e.getMessage());
			return text;
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.MESSAGE;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.MESSAGE;
	}
}
