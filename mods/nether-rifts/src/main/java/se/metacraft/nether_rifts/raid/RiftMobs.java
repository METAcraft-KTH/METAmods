package se.metacraft.nether_rifts.raid;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import se.metacraft.mob_modifiers.extensions.EntityExtensions;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.mixin.HoglinAccessor;
import se.metacraft.nether_rifts.mixin.MobAccessor;
import se.metacraft.nether_rifts.mob.GhastBombardier;
import se.metacraft.nether_rifts.mob.GhastPart;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import se.metacraft.nether_rifts.mob.WarDrummer;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.rift.RiftData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mobs that come through a rift, and what sets them apart from the nether's own. */
public final class RiftMobs {

	/** The rift a mob came through; saved with the mob, so a raid survives a restart. */
	public static final AttachmentType<UUID> RIFT = AttachmentRegistry.create(
			NetherRifts.getID("rift"), builder -> builder.persistent(UUIDUtil.CODEC)
	);

	private RiftMobs() {}

	public static void init() {
		// Vetoed rather than discarded: discarding inside ENTITY_LOAD leaves the entity manager half-registered.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) -> {
			var id = entity.getAttached(RIFT);
			if (id == null && entity instanceof LargeFireball fireball) {
				// A fireball from a rift mob is marked at birth, so it stays ours after the shooter is gone.
				id = fireball.getOwner() == null ? null : fireball.getOwner().getAttached(RIFT);
				if (id != null) {
					fireball.setAttached(RIFT, id);
				}
			}
			return id == null || RiftData.get(level).byId(id).isPresent();
		});
		// A rift ghast's rider glides down when it dies, as a Chained Ghast's crew do, rather than die of the fall.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Ghast && !(entity instanceof WarGhast) && isRiftMob(entity)) {
				for (var rider : entity.getPassengers()) {
					if (rider instanceof LivingEntity living) {
						living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, WarGhast.CREW_SLOW_FALL_TICKS, 0));
					}
				}
			}
		});
	}

	public static Optional<UUID> riftOf(Entity entity) {
		return Optional.ofNullable(entity.getAttached(RIFT));
	}

	public static boolean isRiftMob(@Nullable Entity entity) {
		return entity != null && entity.hasAttached(RIFT);
	}

	/** The rift an entity fights for: its own, or a Chained Ghast's deck's or crew member's, its ghast's (they aren't tagged). */
	public static Optional<UUID> sideOf(@Nullable Entity entity) {
		if (entity instanceof GhastPart part) {
			entity = part.ghast();
		}
		return entity == null ? Optional.empty() : riftOf(entity);
	}

	/** Both fight for the same rift; false when either is of none. */
	public static boolean sameRift(@Nullable Entity a, @Nullable Entity b) {
		var side = sideOf(a);
		return side.isPresent() && side.equals(sideOf(b));
	}

	/**
	 * A hit on the victim from its own side, which never lands: a rift's mobs never hurt each other. The attacker is the
	 * source's (a projectile's owner); only with none left (a shooter gone) its fireball or bomb, marked with its rift.
	 * A rift ghast's fireball a player hit back is the player's, so it still hurts the ghast.
	 */
	public static boolean spares(Entity victim, DamageSource source) {
		var attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		return sameRift(victim, attacker);
	}

	/**
	 * Piglins that don't barter and don't honour the gold-armour truce: rift mobs, and the Ghast Bombardier wherever it
	 * is (a summoned war ghast's Bombardier is of no rift, and still never trades and always fights).
	 */
	public static boolean keepsRiftRules(@Nullable Entity entity) {
		return isRiftMob(entity) || entity instanceof GhastBombardier;
	}

	/** One pick from the slot's roster entry at {@code pos}, pushed by {@code push}; returns every entity spawned. */
	public static List<Entity> spawn(ServerLevel level, UUID rift, Slot slot, BlockPos pos, Vec3 push) {
		var entry = NetherRiftsConfig.getInstance().rosterEntry(slot);
		if (entry == null) {
			return List.of();
		}
		var spawned = new ArrayList<Entity>();
		entry.mobs().getRandom(level.getRandom()).ifPresent(pick -> pick.spawnMobFromNBT(level, pos, entity -> {
			apply(entity, rift, slot);
			if (entity.getVehicle() == null) {
				entity.setDeltaMovement(push);
			}
			spawned.add(entity);
		}));
		// a war ghast brings its own crew: riders put on it (an old config's bombardier entry) would double it
		spawned.removeIf(entity -> {
			if (entity.getVehicle() instanceof WarGhast) {
				entity.discard();
				return true;
			}
			return false;
		});
		return spawned;
	}

	/** Makes a mob one of the rift's, in the slot's role (as if it had come through it): a war ghast's crew. */
	public static void enlist(Entity entity, UUID rift, Slot slot) {
		apply(entity, rift, slot);
	}

	static void apply(Entity entity, UUID rift, Slot slot) {
		entity.setAttached(RIFT, rift);
		// nobody traps a raid's mobs in a boat or a minecart
		((EntityExtensions) entity).metacraft_lib$setPreventEnterVehicle(true);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.setCanPickUpLoot(false);
			for (var equipmentSlot : EquipmentSlot.values()) {
				mob.setDropChance(equipmentSlot, 0f);
			}
			lootTable(mob, slot).ifPresent(table -> ((MobAccessor) mob).netherRifts$setLootTable(Optional.of(table)));
		}
		if (entity instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (entity instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
			// Piglins hunt hoglins; these come out beside them. A jockey's hoglin is the vehicle, so it gets here too.
			((HoglinAccessor) hoglin).netherRifts$setCannotBeHunted(true);
		}
	}

	/**
	 * The drummer, the Bombardier, the General (piglins all, so they are checked first), piglins and brutes drop rift loot by role; war ghasts drop a vanilla ghast's
	 * (a summoned one gets the same from its own table, {@code entities/war_ghast}); ghasts, hoglins and striders
	 * keep their own.
	 */
	static Optional<ResourceKey<LootTable>> lootTable(Mob mob, Slot slot) {
		String role;
		if (mob instanceof WarGhast) {
			return EntityTypes.GHAST.getDefaultLootTable();
		}
		if (mob instanceof WarDrummer) {
			return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/leader")));
		}
		if (mob instanceof GhastBombardier) {
			return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/trooper")));
		}
		if (mob instanceof PiglinGeneral) {
			return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/captain")));
		}
		if (mob instanceof PiglinBrute) {
			role = "elite";
		} else if (mob instanceof AbstractPiglin) {
			role = slot == Slot.LEADER ? "leader" : "trooper";
		} else {
			return Optional.empty();
		}
		return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/" + role)));
	}
}
