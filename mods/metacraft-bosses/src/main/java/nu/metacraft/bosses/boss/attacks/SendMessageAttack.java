package nu.metacraft.bosses.boss.attacks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.bosses.METAcraftBosses;

public class SendMessageAttack extends InstantAttack {

	public static final MapCodec<SendMessageAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("text").forGetter(a -> a.text),
					Codec.BOOL.fieldOf("actionbar").forGetter(a -> a.actionbar)
			).apply(instance, SendMessageAttack::new)
	);

	private final Component text;
	private final boolean actionbar;

	public SendMessageAttack(Component text, boolean actionbar) {
		this.text = text;
		this.actionbar = actionbar;
	}

	public static Component parseText(Component text, BossContext<?> ctx, Entity sender, MutableBoolean errored) {
		try {
			return ComponentUtils.updateForEntity(
					ctx.boss().createCommandSourceStackForNameResolution(ctx.getWorld()).withPermission(LevelBasedPermissionSet.GAMEMASTER),
					text, sender, 0
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
			player.displayClientMessage(
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
