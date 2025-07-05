package nu.metacraft.cutscenes.transitions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.Texts;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

public class MessageTransition extends InstantTransition {

	public static final MapCodec<MessageTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextCodecs.CODEC.fieldOf("message").forGetter(t -> t.message),
					Codec.BOOL.optionalFieldOf("overlay", false).forGetter(t -> t.overlay)
			).apply(instance, MessageTransition::new)
	);

	private final Text message;
	private final boolean overlay;

	public MessageTransition(Text message, boolean overlay) {
		this.message = message;
		this.overlay = overlay;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		player.sendMessage(parseText(player, cutscene, message), overlay);
	}

	public static Text parseText(ServerPlayerEntity player, CutsceneInstance cutscene, Text text) {
		try {
			return Texts.parse(RunCommandTransition.getSource(cutscene, false, player, false), text, player, 0);
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
