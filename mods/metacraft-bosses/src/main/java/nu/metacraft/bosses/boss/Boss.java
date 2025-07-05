package nu.metacraft.bosses.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import nu.metacraft.core.music.ManageableServerBossBar;
import nu.metacraft.core.util.helper.BossBarHelper;

import java.util.List;
import java.util.function.Predicate;

public interface Boss {

	Entity getAsEntity();

	default ServerWorld getServerWorld() {
		return (ServerWorld) getAsEntity().getWorld();
	}

	default ManageableServerBossBar getBossBar() {
		return BossBarHelper.getBossBar((Entity) this).orElseGet(() -> {
			var bossBar = new ManageableServerBossBar(getAsEntity().getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
			BossBarHelper.setBossBar((Entity) this, bossBar);
			return bossBar;
		});
	}

	default boolean isSpectator(Entity entity) {
		return entity == this || entity instanceof ServerPlayerEntity p && (p.isCreative() || p.isSpectator());
	}

	default boolean isEnemy(Entity entity) {
		return !isAlly(entity) && !isSpectator(entity);
	}

	default boolean isAlly(Entity entity) {
		if (isSpectator(entity)) return false;
		if (entity instanceof Ownable ownable && ownable.getOwner() == this) {
			return true;
		}
		if (getAsEntity().getScoreboardTeam() != null) {
			return getAsEntity().isTeammate(entity);
		}
		return entity instanceof HostileEntity;
	}

	default List<Entity> getAllies() {
		return getAllies(TypeFilter.instanceOf(Entity.class), e -> true);
	}

	default <T extends Entity> List<T> getAllies(TypeFilter<Entity, T> filter) {
		return getAllies(filter, e -> true);
	}

	default <T extends Entity> List<T> getAllies(TypeFilter<Entity, T> filter, Predicate<T> isValid) {
		return getServerWorld().getEntitiesByType(
				filter, getAsEntity().getBoundingBox().expand(128),
				isValid.and(this::isAlly)
		);
	}

	default <T extends Entity> List<T> getTargets(TypeFilter<Entity, T> filter, Predicate<T> isValid) {
		return getServerWorld().getEntitiesByType(
				filter, getAsEntity().getBoundingBox().expand(128),
				isValid.and(this::isEnemy)
		);
	}

	default <T extends Entity> List<T> getTargets(TypeFilter<Entity, T> filter) {
		return getTargets(filter, e -> true);
	}

	default List<Entity> getTargets() {
		return getTargets(TypeFilter.instanceOf(Entity.class));
	}

	default List<Entity> getTargets(Predicate<Entity> isValid) {
		return getTargets(TypeFilter.instanceOf(Entity.class), isValid);
	}

	default List<LivingEntity> getLivingTargets() {
		return getTargets(TypeFilter.instanceOf(LivingEntity.class));
	}

	default List<ServerPlayerEntity> getPlayerTargets() {
		return getTargets(TypeFilter.instanceOf(ServerPlayerEntity.class));
	}

}
