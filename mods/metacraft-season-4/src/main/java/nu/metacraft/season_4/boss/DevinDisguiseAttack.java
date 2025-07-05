package nu.metacraft.season_4.boss;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;
import nu.metacraft.bosses.boss.AutoAttackingBoss;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackType;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

public class DevinDisguiseAttack implements Attack {

	public static final MapCodec<DevinDisguiseAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(
							EquipmentSlot.CODEC,
							ItemStack.CODEC
					).optionalFieldOf("prev_equipment", new HashMap<>()).forGetter(
							t -> t.prevEquipment
					)
			).apply(instance, DevinDisguiseAttack::new)
	);

	private ServerPlayerEntity targetEntity;
	private final Map<EquipmentSlot, ItemStack> prevEquipment;

	public DevinDisguiseAttack() {
		this(new HashMap<>());
	}

	public DevinDisguiseAttack(Map<EquipmentSlot, ItemStack> prevEquipment) {
		this.prevEquipment = prevEquipment instanceof ImmutableMap<EquipmentSlot, ItemStack> ? new HashMap<>(prevEquipment) : prevEquipment;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		var targets = ctx.boss().getPlayerTargets();
		if (targets.size() >= 2) {
			targetEntity = targets.get(ctx.random().nextInt(targets.size()));
			targetEntity.setAttacker(null);
			ctx.boss().getBrain().forget(MemoryModuleType.ANGRY_AT);
			ctx.boss().getBrain().forget(MemoryModuleType.ATTACK_TARGET);
			ctx.getAsMob().ifPresent(mob -> mob.setTarget(null));
			EntityTrackerHelper.getEntityTrackers(ctx.getWorld()).get(ctx.boss().getId()).updateTrackedStatus(
					targets
			);
			ctx.boss().setInvisible(true);
			ctx.boss().setInvulnerable(true);
			ctx.boss().setNoGravity(true);
			ctx.boss().setSilent(true);
			for (var slot : EquipmentSlot.values()) {
				var s = ctx.boss().getEquippedStack(slot);
				if (!s.isEmpty()) {
					prevEquipment.put(slot, s);
				}
				ctx.boss().equipStack(slot, ItemStack.EMPTY);
			}
			teleportRandomly(ctx.boss());
		} else {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public OptionalInt getDelayOverride(BossContext<?> ctx) {
		if (ctx.boss().getPlayerTargets().size() < 2) {
			return OptionalInt.of(0);
		}
		return OptionalInt.empty();
	}

	private void teleportRandomly(LivingEntity entity) {
		double minDist = 5;
		double maxDist = 15;

		double rDist = entity.getRandom().nextDouble() * (maxDist - minDist) + minDist;
		double angle = entity.getRandom().nextDouble() * 2 * Math.PI;
		double xDist = Math.cos(angle) * rDist;
		double zDist = Math.sin(angle) * rDist;

		entity.teleport(
				entity.getX() + xDist,
				entity.getY() + 10,
				entity.getZ() + zDist,
				false
		);
	}

	@Override
	public void tick(BossContext<?> ctx) {
		if (!ctx.boss().isInvisible()) {
			ctx.boss().setInvisible(true);
		}
		if (!ctx.boss().isInvulnerable()) {
			ctx.boss().setInvulnerable(true);
		}
		if (!ctx.boss().hasNoGravity()) {
			ctx.boss().setNoGravity(true);
		}
		if (!ctx.boss().isSilent()) {
			ctx.boss().setSilent(true);
		}
		if (targetEntity == null || targetEntity.isDisconnected() || targetEntity.isDead() || targetEntity.getAttacker() instanceof PlayerEntity) {
			ctx.boss().removeAttack(this);
		}
	}

	public static boolean canBeSpectated(AutoAttackingBoss boss, ServerPlayerEntity player) {
		return !boss.getAttacks().hasActiveAttack(a -> a instanceof DevinDisguiseAttack);
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		if (targetEntity != null) {
			ctx.boss().teleportTo(
					new TeleportTarget(
							targetEntity.getWorld(),
							targetEntity.getPos(),
							targetEntity.getVelocity(),
							targetEntity.getYaw(),
							targetEntity.getPitch(),
							TeleportTarget.NO_OP
					)
			);
			if (targetEntity.isAlive()) {
				teleportRandomly(targetEntity);
			}
		}
		ctx.boss().setInvisible(false);
		ctx.boss().setInvulnerable(false);
		ctx.boss().setNoGravity(false);
		ctx.boss().setSilent(false);
		EntityTrackerHelper.getEntityTrackers(ctx.getWorld()).get(ctx.boss().getId()).updateTrackedStatus(
				ctx.boss().getPlayerTargets()
		);
		for (var slot : prevEquipment.keySet()) {
			ctx.boss().equipStack(slot, prevEquipment.get(slot));
		}
	}

	@Override
	public AttackType getType() {
		return Season4Attacks.DEVIN_DISGUISE.value();
	}
}
