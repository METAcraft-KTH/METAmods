package nu.metacraft.nether_rifts.mob;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A crew member on a Chained Ghast's deck, to blows and projectiles: the ghast's model draws the figure, and this box
 * stands where it does ({@link WarGhast#spot}), centred on it and roomier than the mob (the box doesn't turn with the
 * figure). It takes damage as that crew member would (the mob's health; the spear piglin wears the ghast's armour for
 * real here), less the deck's cover: a share of it after armour ({@code mobs.bombardier.crew.damage_taken}). Its hurts
 * are its own, never the ghast's, and flash only its figure red.
 *
 * <p>When it dies the figure goes from the model with the gear it showed, it drops the crew member's loot (in a rift,
 * its role's, as the mob would), and the ghast loses what it did ({@link WarGhast#crewDied}).
 */
public class CrewHitbox extends LivingEntity implements PolymerEntity, GhastPart {

	/** Defaults for {@link NetherRiftsConfig.CrewTuning}: a piglin's box on the deck, and the share of damage it takes. */
	public static final double WIDTH = 1.0;
	public static final double HEIGHT = 2.3;
	public static final double DAMAGE_TAKEN = 0.6;

	@Nullable
	private WarGhast ghast;
	@Nullable
	private WarGhast.Crew crew;

	public CrewHitbox(EntityType<? extends CrewHitbox> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes();
	}

	/** A crew member's full health: its mob's. */
	public static float maxHealth(WarGhast.Crew crew) {
		return (float) DefaultAttributes.getSupplier(crew.type()).getBaseValue(Attributes.MAX_HEALTH);
	}

	/** Joins a ghast as one of its crew, with that much health. */
	void join(WarGhast ghast, WarGhast.Crew crew, float health) {
		this.ghast = ghast;
		this.crew = crew;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth(crew));
		setHealth(health);
		refreshDimensions();
	}

	@Nullable
	@Override
	public WarGhast ghast() {
		return ghast;
	}

	@Nullable
	public WarGhast.Crew crew() {
		return crew;
	}

	/** The configured size for a piglin, scaled to the crew member's mob as a piglin's is to its own. */
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		if (crew == null) {
			return super.getDefaultDimensions(pose);
		}
		var tuning = tuning();
		var mob = crew.type().getDimensions();
		var piglin = EntityTypes.PIGLIN.getDimensions();
		return EntityDimensions.scalable((float) (tuning.hitboxWidth() * mob.width() / piglin.width()),
				(float) (tuning.hitboxHeight() * mob.height() / piglin.height()));
	}

	/** After armour, enchantments and effects, the deck's cover. */
	@Override
	protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
		return (float) (super.getDamageAfterMagicAbsorb(source, damage) * tuning().damageTaken());
	}

	private static NetherRiftsConfig.CrewTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().bombardier().crew();
	}

	/** Wears what the crew member wears (only the spear piglin wears anything: the ghast's armour). */
	void wear(EquipmentSlot slot, ItemStack stack) {
		if (!ItemStack.matches(getItemBySlot(slot), stack)) {
			setItemSlot(slot, stack.copy());
		}
	}

	@Override
	public void tick() {
		// the ghast puts it in place every tick; nothing else moves it
		setDeltaMovement(Vec3.ZERO);
		super.tick();
		if (!level().isClientSide() && (ghast == null || ghast.isRemoved() || !ghast.isAlive())) {
			discard();
		}
	}

	/** The figure puffs away at once, as a mob does at the end of its death. */
	@Override
	public void die(DamageSource source) {
		boolean dying = !isRemoved() && !dead;
		super.die(source);
		if (dying && level() instanceof ServerLevel level) {
			var box = getBoundingBox();
			level.sendParticles(ParticleTypes.POOF, box.getCenter().x, box.getCenter().y, box.getCenter().z, 20,
					box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, 0.02);
			if (ghast != null && crew != null) {
				ghast.crewDied(crew);
			}
		}
	}

	@Override
	protected void tickDeath() {
		if (!level().isClientSide() && !isRemoved()) {
			remove(RemovalReason.KILLED);
		}
	}

	/**
	 * In a rift, the loot of the crew member's role (the drummer the leader's, the rest a trooper's, as {@link RiftMobs}
	 * gives a war ghast's crew); otherwise the mob's own.
	 */
	@Override
	public Optional<ResourceKey<LootTable>> getLootTable() {
		if (crew == null) {
			return Optional.empty();
		}
		if (ghast != null && RiftMobs.isRiftMob(ghast)) {
			var role = crew == WarGhast.Crew.DRUMMER ? "leader" : "trooper";
			return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/" + role)));
		}
		return crew.type().getDefaultLootTable();
	}

	/** A piglin's (a monster's). */
	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 5;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_DEATH;
	}

	@Override
	protected Component getTypeName() {
		return crew == null ? super.getTypeName() : crew.type().getDescription();
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	// it stays put on the deck: nothing pushes or knocks it about

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.INTERACTION;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		GhastPart.asInteraction(this, data, initial);
	}

	/** An interaction wears nothing. */
	@Override
	public List<Pair<EquipmentSlot, ItemStack>> getPolymerVisibleEquipment(List<Pair<EquipmentSlot, ItemStack>> items, ServerPlayer player) {
		return List.of();
	}
}
