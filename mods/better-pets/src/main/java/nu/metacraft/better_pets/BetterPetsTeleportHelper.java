package nu.metacraft.better_pets;

import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypeFilter;
import nu.metacraft.lib.util.helper.TeleportHelper;

import java.util.List;

public class BetterPetsTeleportHelper {

	public static void collectPets(
			Entity e,
			LocalRef<List<? extends Entity>> leashed,
			LocalRef<List<? extends LivingEntity>> pets
	) {
		if (e.getEntityWorld() instanceof ServerWorld world) {
			leashed.set(
					world.getEntitiesByType(
							TypeFilter.instanceOf(Entity.class),
							entity -> entity instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == e
					)
			);
			pets.set(world.getEntitiesByType(
					TypeFilter.instanceOf(TameableEntity.class),
					entity ->
							((TameableExtension) entity).metacraft$getCurrentFollowTarget() == e
							&& !entity.cannotFollowOwner()
			));
			pets.get().forEach(pet -> {
				world.getChunkManager().addTicket(
						TeleportHelper.TELEPORT_MOB_SOON, pet.getChunkPos(), 2
				);
			});
			leashed.get().forEach(pet -> {
				world.getChunkManager().addTicket(
						TeleportHelper.TELEPORT_MOB_SOON, pet.getChunkPos(), 2
				);
			});
		}
	}

	public static void teleportPets(
			Entity entity,
			LocalRef<List<? extends Entity>> leashed,
			LocalRef<List<? extends LivingEntity>> pets
	) {
		if (entity.getEntityWorld() instanceof ServerWorld world) {
			leashed.get().forEach(l -> {
				((Leashable) l).detachLeashWithoutDrop();
				TeleportHelper.teleportEntityToPlayer(
						entity, l,
						e -> ((Leashable) e).attachLeash(entity, true),
						e -> {
							e.dropItem(world, Items.LEAD);
							world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_LEAD_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
						}
				);
			});
			pets.get().forEach(pet -> {
				TeleportHelper.teleportEntityToPlayer(entity, pet);
			});
		}
	}

}
