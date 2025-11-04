package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

public class SendTitleAttack implements Attack {

	public static final MapCodec<SendTitleAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("title").forGetter(a -> a.title),
					ComponentSerialization.CODEC.optionalFieldOf("subtitle").forGetter(a -> a.subtitle),
					Times.CODEC.optionalFieldOf("times", Times.DEFAULT).forGetter(a -> a.times)
			).apply(instance, SendTitleAttack::new)
	);

	private final Component title;
	private final Optional<Component> subtitle;
	private final Times times;

	private int time;

	public SendTitleAttack(Component title, Optional<Component> subtitle) {
		this(title, subtitle, Times.DEFAULT);
	}

	public SendTitleAttack(Component title, Optional<Component> subtitle, Times times) {
		this.title = title;
		this.subtitle = subtitle;
		this.times = times;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		time = times.getTotalTime();
		MutableBoolean errored = new MutableBoolean(false);
		ctx.boss().getPlayerTargets().forEach(player -> {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(times.fadeInTicks, times.stayTicks, times.fadeOutTicks));
			player.connection.send(new ClientboundSetTitleTextPacket(
					SendMessageAttack.parseText(title, ctx, player, errored)
			));
			subtitle.ifPresent(subtitle -> {
				player.connection.send(new ClientboundSetSubtitleTextPacket(
						SendMessageAttack.parseText(subtitle, ctx, player, errored)
				));
			});
		});
	}

	@Override
	public void tick(BossContext<?> ctx) {
		if (time-- <= 0) {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		ctx.boss().getPlayerTargets().forEach(player -> {
			player.connection.send(new ClientboundClearTitlesPacket(true));
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SEND_TITLE;
	}

	public record Times(int fadeInTicks, int stayTicks, int fadeOutTicks) {
		public static final Codec<Times> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.optionalFieldOf("fadeInTicks", 10).forGetter(Times::fadeInTicks),
						Codec.INT.optionalFieldOf("stayTicks", 70).forGetter(Times::stayTicks),
						Codec.INT.optionalFieldOf("fadeOutTicks", 20).forGetter(Times::fadeOutTicks)
				).apply(instance, Times::new)
		);

		public static final Times DEFAULT = Times.CODEC.parse(
				JavaOps.INSTANCE, Map.of()
		).result().get();

		public int getTotalTime() {
			return fadeInTicks + stayTicks + fadeInTicks;
		}
	}
}
