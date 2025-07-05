package nu.metacraft.bosses.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import nu.metacraft.bosses.boss.attacks.Attack;

import java.util.*;
import java.util.function.Predicate;

public interface AutoAttackingBoss extends Boss {

	int getMaxAttacks();

	AttackContainer getAttacks();

	Attack.BossContext<?> getContext(Attack attack);

	int getNewAttackDelay(Attack chosen);
	Optional<Attack> chooseAttack();

	default boolean addAttack(Attack attack) {
		return getAttacks().addAttack(attack);
	}

	default void removeAttack(Attack attack) {
		getAttacks().removeAttack(attack);
	}

	default void removeAllAttacks() {
		getAttacks().removeAllAttacks();
	}

	class AttackContainer {

		private static final String ATTACK_CONTAINER = "attack_container";

		private PSet<Attack> currentAttacks = HashTreePSet.empty();
		private int timeUntilNextAttack;

		private final AutoAttackingBoss boss;

		public AttackContainer(AutoAttackingBoss boss) {
			this.boss = boss;
		}

		protected boolean addAttack(Attack attack) {
			attack = attack.copy(boss.getServerWorld().getRegistryManager());
			for (var a : currentAttacks) {
				if (!a.compatibleWith(attack) || !attack.compatibleWith(a)) {
					return false;
				}
			}
			if (attack.isInstant()) {
				attack.activate(boss.getContext(attack));
				return true;
			}
			if (currentAttacks.size() >= boss.getMaxAttacks()) return false;
			if (attack.isMovement() && currentAttacks.stream().anyMatch(Attack::isMovement)) {
				return false;
			}
			currentAttacks = currentAttacks.plus(attack);
			attack.activate(boss.getContext(attack));
			return true;
		}

		protected void removeAttack(Attack attack) {
			if (attack.isInstant()) {
				return;
			}
			if (currentAttacks.contains(attack)) {
				currentAttacks = currentAttacks.minus(attack);
				attack.deactivate(boss.getContext(attack));
			} else {
				currentAttacks.forEach(a -> a.removeSubAttack(boss.getContext(attack), attack));
			}
		}

		protected void removeAllAttacks() {
			currentAttacks.forEach(this::removeAttack);
		}

		public void tickAttackDelay() {
			if (timeUntilNextAttack-- <= 0) {
				boss.chooseAttack().ifPresent(attack -> {
					timeUntilNextAttack = attack.getDelayOverride(boss.getContext(attack)).orElse(boss.getNewAttackDelay(attack));
					if (!boss.addAttack(attack)) {
						timeUntilNextAttack = 0;
					}
				});
			}
		}

		public boolean hasActiveAttack(Predicate<Attack> attack) {
			return currentAttacks.stream().anyMatch(attack);
		}

		public void tickAttacks() {
			currentAttacks.forEach(attack -> attack.tick(boss.getContext(attack)));
		}

		public void writeNBT(WriteView nbt) {
			nbt.put(
					ATTACK_CONTAINER,
					Serialized.CODEC,
					save()
			);
		}

		public void readNBT(ReadView nbt) {
			nbt.read(ATTACK_CONTAINER, Serialized.CODEC).ifPresentOrElse(
					this::load,
					this::removeAllAttacks
			);
		}

		public Serialized save() {
			return new Serialized(currentAttacks.stream().toList(), timeUntilNextAttack);
		}

		public void load(Serialized data) {
			currentAttacks = currentAttacks.plusAll(data.currentAttacks);
			timeUntilNextAttack = data.timeUntilNextAttack;
		}

		public record Serialized(List<Attack> currentAttacks, int timeUntilNextAttack) {
			public static final Codec<Serialized> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							Attack.REGISTRY_CODEC.listOf().fieldOf("current_attack").forGetter(Serialized::currentAttacks),
							Codec.INT.fieldOf("time_until_next_attack").forGetter(Serialized::timeUntilNextAttack)
					).apply(instance, Serialized::new)
			);
		}
	}
}
