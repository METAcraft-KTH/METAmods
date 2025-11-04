package nu.metacraft.bosses.boss;

import nu.metacraft.core.music.ManageableServerBossBar;
import nu.metacraft.core.util.helper.BossBarHelper;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.entity.EntityTypeTest;

public interface Boss {

	Entity getAsEntity();

	default ServerLevel getServerWorld() {
		return (ServerLevel) getAsEntity().level();
	}

	default ManageableServerBossBar getBossBar() {
		return BossBarHelper.getBossBar((Entity) this).orElseGet(() -> {
			var bossBar = new ManageableServerBossBar(getAsEntity().getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
			BossBarHelper.setBossBar((Entity) this, bossBar);
			return bossBar;
		});
	}

	default boolean isSpectator(Entity entity) {
		return entity == this || entity instanceof ServerPlayer p && (p.isCreative() || p.isSpectator());
	}

	default boolean isEnemy(Entity entity) {
		return !isAlly(entity) && !isSpectator(entity);
	}

	default boolean isAlly(Entity entity) {
		if (isSpectator(entity)) return false;
		if (entity instanceof TraceableEntity ownable && ownable.getOwner() == this) {
			return true;
		}
		if (getAsEntity().getTeam() != null) {
			return getAsEntity().isAlliedTo(entity);
		}
		return entity instanceof Monster;
	}

	default List<Entity> getAllies() {
		return getAllies(EntityTypeTest.forClass(Entity.class), e -> true);
	}

	default <T extends Entity> List<T> getAllies(EntityTypeTest<Entity, T> filter) {
		return getAllies(filter, e -> true);
	}

	default <T extends Entity> List<T> getAllies(EntityTypeTest<Entity, T> filter, Predicate<T> isValid) {
		return getServerWorld().getEntities(
				filter, getAsEntity().getBoundingBox().inflate(128),
				isValid.and(this::isAlly)
		);
	}

	default <T extends Entity> List<T> getTargets(EntityTypeTest<Entity, T> filter, Predicate<T> isValid) {
		return getServerWorld().getEntities(
				filter, getAsEntity().getBoundingBox().inflate(128),
				isValid.and(this::isEnemy)
		);
	}

	default <T extends Entity> List<T> getTargets(EntityTypeTest<Entity, T> filter) {
		return getTargets(filter, e -> true);
	}

	default List<Entity> getTargets() {
		return getTargets(EntityTypeTest.forClass(Entity.class));
	}

	default List<Entity> getTargets(Predicate<Entity> isValid) {
		return getTargets(EntityTypeTest.forClass(Entity.class), isValid);
	}

	default List<LivingEntity> getLivingTargets() {
		return getTargets(EntityTypeTest.forClass(LivingEntity.class));
	}

	default List<ServerPlayer> getPlayerTargets() {
		return getTargets(EntityTypeTest.forClass(ServerPlayer.class));
	}

}
