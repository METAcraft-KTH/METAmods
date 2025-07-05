package nu.metacraft.bosses.boss.attacks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.Texts;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.bosses.METAcraftBosses;

public class SendMessageAttack extends InstantAttack {

	public static final MapCodec<SendMessageAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextCodecs.CODEC.fieldOf("text").forGetter(a -> a.text),
					Codec.BOOL.fieldOf("actionbar").forGetter(a -> a.actionbar)
			).apply(instance, SendMessageAttack::new)
	);

	private final Text text;
	private final boolean actionbar;

	public SendMessageAttack(Text text, boolean actionbar) {
		this.text = text;
		this.actionbar = actionbar;
	}

	public static Text parseText(Text text, BossContext<?> ctx, Entity sender, MutableBoolean errored) {
		try {
			return Texts.parse(
					ctx.boss().getCommandSource(ctx.getWorld()).withLevel(2), text, sender, 0
			);
		} catch (CommandSyntaxException e) {
			if (!errored.booleanValue()) {
				METAcraftBosses.LOGGER.error(e.getMessage());
				errored.setTrue();
			}
		}
		return text;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		MutableBoolean errored = new MutableBoolean(false);
		ctx.boss().getPlayerTargets().forEach(player -> {
			player.sendMessage(
					parseText(text, ctx, player, errored),
					actionbar
			);
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SEND_MESSAGE;
	}
}
