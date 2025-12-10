package nu.metacraft.better_pets;

import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import nu.metacraft.lib.util.helper.TeleportHelper;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;

public class BetterPetsTeleportHelper {

	public static void collectPets(
			Entity e,
			LocalRef<List<? extends Entity>> leashed,
			LocalRef<List<? extends LivingEntity>> pets
	) {
		if (e.level() instanceof ServerLevel world) {
			leashed.set(
					world.getEntities(
							EntityTypeTest.forClass(Entity.class),
							entity -> entity instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == e
					)
			);
			pets.set(world.getEntities(
					EntityTypeTest.forClass(TamableAnimal.class),
					entity ->
							((TameableExtension) entity).metacraft$getCurrentFollowTarget() == e
							&& !entity.unableToMoveToOwner()
			));
			pets.get().forEach(pet -> {
				world.getChunkSource().addTicketWithRadius(
						TeleportHelper.TELEPORT_MOB_SOON, pet.chunkPosition(), 2
				);
			});
			leashed.get().forEach(pet -> {
				world.getChunkSource().addTicketWithRadius(
						TeleportHelper.TELEPORT_MOB_SOON, pet.chunkPosition(), 2
				);
			});
		}
	}

	public static void teleportPets(
			Entity entity,
			LocalRef<List<? extends Entity>> leashed,
			LocalRef<List<? extends LivingEntity>> pets
	) {
		if (entity.level() instanceof ServerLevel world) {
			leashed.get().forEach(l -> {
				((Leashable) l).removeLeash();
				TeleportHelper.teleportEntityToPlayer(
						entity, l,
						e -> ((Leashable) e).setLeashedTo(entity, true),
						e -> {
							e.spawnAtLocation(world, Items.LEAD);
							world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
						}
				);
			});
			pets.get().forEach(pet -> {
				TeleportHelper.teleportEntityToPlayer(entity, pet);
			});
		}
	}

}
