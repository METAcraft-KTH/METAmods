package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;
import se.metacraft.bosses.boss.attacks.target.FixedPos;
import se.metacraft.bosses.boss.attacks.target.PositionTargetSelector;

import java.util.Optional;

public class MoveTowardsAttack implements Attack {

	public static final MapCodec<MoveTowardsAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PositionTargetSelector.REGISTRY_CODEC.fieldOf("selector").forGetter(m -> m.selector),
					Codec.DOUBLE.fieldOf("speed").forGetter(m -> m.speed),
					Codec.floatRange(0, 1).fieldOf("chanceToTeleportIfStuck").forGetter(m -> m.chanceToTeleportIfStuck),
					OnArrival.CODEC.optionalFieldOf("onArrival").forGetter(m -> m.onArrival),
					Vec3d.CODEC.optionalFieldOf("selected", Vec3d.ZERO).forGetter(m -> m.selected)
			).apply(instance, MoveTowardsAttack::new)
	);

	private final PositionTargetSelector selector;
	private final double speed;
	private final float chanceToTeleportIfStuck;
	private final Optional<OnArrival> onArrival;
	private Vec3d selected = Vec3d.ZERO;

	private int timeStuck = 0;

	public MoveTowardsAttack(PositionTargetSelector selector, double speed, float chanceToTeleportIfStuck, Optional<OnArrival> onArrival) {
		this.selector = selector;
		this.speed = speed;
		this.chanceToTeleportIfStuck = chanceToTeleportIfStuck;
		this.onArrival = onArrival;
	}

	public MoveTowardsAttack(PositionTargetSelector selector, double speed, float chanceToTeleportIfStuck, Optional<OnArrival> onArrival, Vec3d selected) {
		this(selector, speed, chanceToTeleportIfStuck, onArrival);
		this.selected = selected;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		selector.getTargetInWorld(ctx).ifPresentOrElse(pos -> {
			selected = pos;
		}, () -> {
			ctx.boss().removeAttack(this);
		});
	}

	@Override
	public void tick(BossContext<?> ctx) {
		ctx.getAsMob().ifPresent(mob -> {
			mob.getMoveControl().moveTo(selected.getX(), selected.getY(), selected.getZ(), speed);
		});
		if (ctx.boss().getBoundingBox().contains(selected)) {
			onArrival.ifPresent(arrival -> {
				ctx.boss().addAttack(arrival.attack);
			});
			ctx.boss().removeAttack(this);
		}
		if (ctx.boss().horizontalCollision || ctx.boss().verticalCollision) {
			timeStuck++;
			if (timeStuck > 20) {
				if (ctx.random().nextFloat() < chanceToTeleportIfStuck) {
					ctx.boss().addAttack(new TeleportAttack(new FixedPos(selected), onArrival.map(arr -> arr.attack)));
				} else {
					onArrival.ifPresent(arrival -> {
						if (!arrival.mustReachDestinationFirst) {
							ctx.boss().addAttack(arrival.attack);
						}
					});
				}
				ctx.boss().removeAttack(this);
			}
		} else {
			timeStuck = 0;
		}
	}

	@Override
	public boolean isMovement() {
		return true;
	}

	@Override
	public void deactivate(BossContext<?> ctx) {

	}

	@Override
	public AttackType getType() {
		return AttackRegistry.MOVE_TOWARDS;
	}

	public record OnArrival(Attack attack, boolean mustReachDestinationFirst) {
		public static final Codec<OnArrival> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).fieldOf("attack").forGetter(arrival -> arrival.attack),
						Codec.BOOL.optionalFieldOf("mustReachDestinationFirst", false).forGetter(arrival -> arrival.mustReachDestinationFirst)
				).apply(instance, OnArrival::new)
		);
	}
}
