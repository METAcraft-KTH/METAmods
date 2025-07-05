package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.bosses.boss.attacks.target.PositionTargetSelector;

import java.util.Optional;

public class TeleportAttack extends InstantAttack {

	private static final int PARTICLES = 50;
	private static final float VOLUME = 1000;
	private static final float PITCH = 1;

	public static final MapCodec<TeleportAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PositionTargetSelector.REGISTRY_CODEC.fieldOf("selector").forGetter(a -> a.selector),
					Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).optionalFieldOf("onArrival").forGetter(a -> a.onArrival),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("particle_count", PARTICLES).forGetter(a -> a.particleCount),
					Codecs.NON_NEGATIVE_FLOAT.optionalFieldOf("volume", VOLUME).forGetter(a -> a.volume),
					Codec.floatRange(0.5f, 2f).optionalFieldOf("pitch", PITCH).forGetter(a -> a.pitch)
			).apply(instance, TeleportAttack::new)
	);

	private final PositionTargetSelector selector;
	private final Optional<Attack> onArrival;
	private final int particleCount;
	private final float volume;
	private final float pitch;

	public TeleportAttack(
			PositionTargetSelector selector, Optional<Attack> onArrival,
			int particleCount, float volume, float pitch
	) {
		this.selector = selector;
		this.onArrival = onArrival;
		this.particleCount = particleCount;
		this.volume = volume;
		this.pitch = pitch;
	}

	public TeleportAttack(PositionTargetSelector selector, Optional<Attack> onArrival) {
		this(selector, onArrival, PARTICLES, VOLUME, PITCH);
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		selector.getTargetInWorld(ctx).ifPresent(target -> {
			double size = (ctx.boss().getWidth() + ctx.boss().getHeight()) / 4;
			double delta = size/2;
			Vec3d srcCenter = ctx.boss().getBoundingBox().getCenter();
			ctx.getWorld().spawnParticles(
					ParticleTypes.PORTAL, srcCenter.getX(), srcCenter.getY(), srcCenter.getZ(), particleCount,
					delta, delta, delta, size
			);
			ctx.boss().teleport(target.x, target.y, target.z, false);
			Vec3d targetCenter = ctx.boss().getBoundingBox().getCenter();
			if (particleCount > 0) {
				ctx.getWorld().getPlayers().forEach(player -> {
					ctx.getWorld().spawnParticles(
							player, ParticleTypes.REVERSE_PORTAL, true, true,
							targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(), particleCount,
							delta, delta, delta, size
					);
				});
			}
			if (volume > 0) {
				ctx.getWorld().playSound(
						null, targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(), SoundEvents.ENTITY_PLAYER_TELEPORT,
						SoundCategory.HOSTILE, volume, pitch
				);
			}
			onArrival.ifPresent(onArrival -> ctx.boss().addAttack(onArrival));
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.TELEPORT;
	}
}
