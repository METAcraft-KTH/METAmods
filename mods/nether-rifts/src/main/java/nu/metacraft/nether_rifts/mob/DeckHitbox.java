package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Chained Ghast's wooden deck, to projectiles: they hit it and glance off (as off anything they can't hurt), with
 * the knock of wood. Nothing hurts it, and a blow does nothing. The size of the deck's floor, under the crew.
 */
public class DeckHitbox extends Entity implements PolymerEntity, GhastPart {

	@Nullable
	private WarGhast ghast;

	public DeckHitbox(EntityType<? extends DeckHitbox> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	void join(WarGhast ghast) {
		this.ghast = ghast;
	}

	@Nullable
	@Override
	public WarGhast ghast() {
		return ghast;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getDirectEntity() instanceof Projectile projectile) {
			level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1.0f, 0.8f);
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && (ghast == null || ghast.isRemoved() || !ghast.isAlive())) {
			discard();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.INTERACTION;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		GhastPart.asInteraction(this, data, initial);
	}
}
