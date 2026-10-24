package se.metacraft.nether_rifts.mob;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.data.EntityData;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * An invisible vanilla mob standing in for an entity, which clients take for the thing they hit. The crosshair shows its
 * attack indicator only over a living entity, and the box a client would pick otherwise isn't one (BIL's interaction for
 * its mobs, {@link HeldItems.Holder}; a crew member's on a Chained Ghast's deck, {@link CrewHitbox}): those go to clients
 * zero wide, so they're never picked, and the stand-in is. A ghast stands in for a Chained Ghast and a piglin for
 * everything else (the drummer and the crew are piglins), scaled to the entity's height: exactly the drummer's and the
 * ghast's boxes; a crew member's is roomier than a piglin, for arrows, which the server takes against the full box.
 * A blow or a click on it is the entity's. It's silent and does nothing on its own; it burns as the entity does (the
 * zero-wide interaction shows no flames) and goes when it dies.
 */
public final class HitStandIn extends ElementHolder {

	private final Entity entity;
	private final EntityType<?> type;
	@Nullable
	private SimpleEntityElement standIn;
	private float scale;

	private HitStandIn(Entity entity) {
		this.entity = entity;
		this.type = entity instanceof Ghast ? EntityTypes.GHAST : EntityTypes.PIGLIN;
		var standIn = new SimpleEntityElement(type);
		standIn.setInvisible(true);
		standIn.getSyncedData().set(EntityData.SILENT, true);
		standIn.getSyncedData().set(EntityData.NO_GRAVITY, true);
		standIn.setInteractionHandler(VirtualElement.InteractionHandler.redirect(entity));
		this.standIn = addElement(standIn);
	}

	/** A stand-in on {@code entity}, as tall as its box is now; {@link #resize} it as the box changes. */
	public static HitStandIn attach(Entity entity) {
		var standIn = new HitStandIn(entity);
		standIn.resize(entity.getBbHeight());
		EntityAttachment.ofTicking(standIn, entity);
		return standIn;
	}

	/** Scales the stand-in to a box this tall. */
	public void resize(float height) {
		float scale = scale(type, height);
		if (scale != this.scale) {
			this.scale = scale;
			if (standIn != null) {
				sendPacket(scalePacket(standIn));
			}
		}
	}

	/** The scale that makes a {@code type} this tall. */
	public static float scale(EntityType<?> type, float height) {
		return height / type.getDimensions().height();
	}

	/** The stand-in clients hit; null once its entity is dying. */
	@Nullable
	public SimpleEntityElement standIn() {
		return standIn;
	}

	public float scale() {
		return scale;
	}

	@Override
	protected void onTick() {
		super.onTick();
		if (standIn == null) {
			return;
		}
		// a dying mob is no target: clients don't show the indicator over one
		if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
			removeElement(standIn);
			standIn = null;
			return;
		}
		standIn.setOnFire(entity.isOnFire());
	}

	private ClientboundUpdateAttributesPacket scalePacket(SimpleEntityElement standIn) {
		var attribute = new AttributeInstance(Attributes.SCALE, instance -> {});
		attribute.setBaseValue(scale);
		return new ClientboundUpdateAttributesPacket(standIn.getEntityId(), List.of(attribute));
	}

	@Override
	protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> packetConsumer) {
		super.startWatchingExtraPackets(player, packetConsumer);
		if (standIn != null) {
			packetConsumer.accept(scalePacket(standIn));
		}
	}
}
