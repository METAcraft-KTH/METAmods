package se.metacraft.east_vs_west;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import se.metacraft.lib.util.helper.VanishHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hats: an item display riding the wearer, so it sits on top of their head and moves with them on every
 * client without lag, turned each tick to face where the head faces. It is a virtual entity (Polymer), sent
 * to whoever can see the wearer: nothing is in the wearer's inventory or helmet slot, nothing can be dropped
 * or taken, and a vanilla client shows it.
 *
 * <p>Who wears what is {@link EventService#hatFor}, asked every tick, so a hat appears, changes and goes
 * away by itself: on reaching the threshold, entering or leaving the top, a respawn, a config reload.
 */
final class Cosmetics {

	private static final class Worn {
		final ServerPlayer player;
		final EvwConfig.Hat hat;
		final ElementHolder holder;
		final ItemDisplayElement display;
		float yaw = Float.NaN;

		Worn(ServerPlayer player, EvwConfig.Hat hat, ElementHolder holder, ItemDisplayElement display) {
			this.player = player;
			this.hat = hat;
			this.holder = holder;
			this.display = display;
		}
	}

	private final Map<UUID, Worn> worn = new HashMap<>();

	void tick(MinecraftServer server, EventService service) {
		var legendary = EvwConfig.get().legendary();
		boolean particles = legendary.particles() && server.getTickCount() % legendary.particleIntervalTicks() == 0;
		for (var player : server.getPlayerList().getPlayers()) {
			var uuid = player.getUUID();
			var hat = shows(player) ? service.hatFor(player) : null;
			var current = worn.get(uuid);
			// A respawn makes a new player entity, which the old display isn't riding.
			if (current != null && (current.player != player || !current.hat.equals(hat))) {
				current.holder.destroy();
				worn.remove(uuid);
				current = null;
			}
			if (current == null && hat != null) {
				current = attach(player, hat);
				if (current != null) {
					worn.put(uuid, current);
				}
			}
			if (current == null) {
				continue;
			}
			float yaw = player.getYHeadRot();
			if (yaw != current.yaw) {
				current.yaw = yaw;
				current.display.setYaw(yaw);
			}
			if (particles && service.isLegendary(uuid)) {
				// Portal particles start away from the point they are sent to and drift in to it, as at an ender chest.
				player.level().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + player.getBbHeight() + 0.25, player.getZ(),
						3, 0.15, 0.1, 0.15, 0.6);
			}
		}
	}

	void onLeave(ServerPlayer player) {
		var current = worn.remove(player.getUUID());
		if (current != null) {
			current.holder.destroy();
		}
	}

	void clear() {
		worn.values().forEach(current -> current.holder.destroy());
		worn.clear();
	}

	/** No hat on someone who shouldn't be seen: it would give away where they are. */
	private static boolean shows(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isInvisible() && !VanishHelper.isVanished(player);
	}

	@Nullable
	private static Worn attach(ServerPlayer player, EvwConfig.Hat hat) {
		var stack = hat.stack();
		if (stack.isEmpty()) {
			return null;
		}
		hat.itemModel().ifPresent(model -> stack.set(DataComponents.ITEM_MODEL, model));
		var display = new ItemDisplayElement(stack);
		// NONE: the model as it is, with none of the item's own display transforms.
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setTransformation(new Matrix4f().translate(0f, hat.yOffset(), 0f).scale(hat.scale().x(), hat.scale().y(), hat.scale().z()));
		display.setTeleportDuration(1);
		display.setDisplaySize(1f, 1f);
		var holder = new ElementHolder();
		holder.addPassengerElement(display);
		EntityAttachment.ofTicking(holder, player);
		return new Worn(player, hat, holder, display);
	}
}
