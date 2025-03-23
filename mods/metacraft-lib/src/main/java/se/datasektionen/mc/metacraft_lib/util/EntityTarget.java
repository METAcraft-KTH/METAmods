package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.Targeter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class EntityTarget {

	private final ServerWorld world;
	private Either<Entity, UUID> target = null;

	private final Context context;
	
	public EntityTarget(
			ServerWorld world, Context context
	) {
		this.world = world;
		this.context = context;
	}

	public static EntityTarget create(World world, Context context) {
		if (world instanceof ServerWorld) {
			return new EntityTarget((ServerWorld) world, context);
		} else {
			return new EntityTarget(null, context);
		}
	}

	protected Optional<Entity> getCachedEntity() {
		return Optional.ofNullable(target).flatMap(owner -> owner.left());
	}

	public Optional<UUID> getID() {
		return Optional.ofNullable(target).map(owner -> owner.map(Entity::getUuid, id -> id));
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
						var player = world.getServer().getPlayerManager().getPlayer(id);
						if (player != null) {
							set(player);
							return;
						}
					}
					if (context.otherDimLookup) {
						for (var world : world.getServer().getWorlds()) {
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
							findOwner.accept(owner.getUuid());
						}
					}
				});
			}
		);
	}

	public void writeNBT(NbtCompound nbt, String name) {
		if (context.serialiseAsString) {
			getID().ifPresent(
					id -> nbt.putString(name, id.toString())
			);
		} else {
			getID().ifPresent(
					id -> nbt.put(name, Uuids.INT_STREAM_CODEC, id)
			);
		}
	}

	public void readNBT(NbtCompound nbt, String name) {
		nbt.get(name, Uuids.CODEC).ifPresent(this::set);
	}

	public record Context(
			boolean otherDimLookup, boolean prioritisePlayers, boolean serialiseAsString,
			Runnable onTargetRemoved
	) {}

	public interface CanSetTarget extends Targeter {
		void setTarget(Entity target);
	}

	public interface CanSetOwner extends Ownable {
		void setOwner(Entity owner);
	}
}
