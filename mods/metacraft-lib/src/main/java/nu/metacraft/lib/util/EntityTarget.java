package nu.metacraft.lib.util;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class EntityTarget {

	private final ServerLevel world;
	private Either<Entity, UUID> target = null;

	private final Context context;
	
	public EntityTarget(
			ServerLevel world, Context context
	) {
		this.world = world;
		this.context = context;
	}

	public static EntityTarget create(Level world, Context context) {
		if (world instanceof ServerLevel) {
			return new EntityTarget((ServerLevel) world, context);
		} else {
			return new EntityTarget(null, context);
		}
	}

	protected Optional<Entity> getCachedEntity() {
		return Optional.ofNullable(target).flatMap(owner -> owner.left());
	}

	public Optional<UUID> getID() {
		return Optional.ofNullable(target).map(owner -> owner.map(Entity::getUUID, id -> id));
	}

	public void remove() {
		set((Either<Entity, UUID>) null);
	}

	protected void removeAndTrigger() {
		remove();
		context.onTargetRemoved().run();
	}

	public void set(Entity entity) {
		set(Optional.ofNullable(entity).map(Either::<Entity, UUID>left).orElse(null));
	}

	public void set(UUID uuid) {
		set(Optional.ofNullable(uuid).map(Either::<Entity, UUID>right).orElse(null));
	}

	protected void set(Either<Entity, UUID> target) {
		this.target = target;
	}

	public Optional<Entity> getEntity() {
		update();
		return getCachedEntity();
	}

	public void update() {
		if (world == null) return;
		Optional.ofNullable(target).ifPresent(
			o -> {
				Consumer<UUID> findOwner = id -> {
					if (context.prioritisePlayers) {
						var player = world.getServer().getPlayerList().getPlayer(id);
						if (player != null) {
							set(player);
							return;
						}
					}
					if (context.otherDimLookup) {
						for (var world : world.getServer().getAllLevels()) {
							var entity = world.getEntity(id);
							if (entity != null) {
								set(entity);
								return;
							}
						}
					} else {
						set(world.getEntity(id));
						return;
					}
					removeAndTrigger();
				};
				o.ifRight(findOwner);
				o.ifLeft(owner -> {
					if (owner.isRemoved()) {
						if (owner.getRemovalReason().shouldDestroy()) {
							removeAndTrigger();
						}
						if (owner.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
							findOwner.accept(owner.getUUID());
						}
					}
				});
			}
		);
	}

	public void writeNBT(ValueOutput nbt, String name) {
		if (context.serialiseAsString) {
			getID().ifPresent(
					id -> nbt.putString(name, id.toString())
			);
		} else {
			getID().ifPresent(
					id -> nbt.store(name, UUIDUtil.CODEC, id)
			);
		}
	}

	public void readNBT(ValueInput nbt, String name) {
		nbt.read(name, UUIDUtil.AUTHLIB_CODEC).ifPresent(this::set);
	}

	public record Context(
			boolean otherDimLookup, boolean prioritisePlayers, boolean serialiseAsString,
			Runnable onTargetRemoved
	) {}

	public interface CanSetTarget extends Targeting {
		void setTarget(Entity target);
	}

	public interface CanSetOwner extends TraceableEntity {
		void setOwner(Entity owner);
	}
}
