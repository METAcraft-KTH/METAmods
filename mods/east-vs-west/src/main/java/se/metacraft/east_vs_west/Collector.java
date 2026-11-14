package se.metacraft.east_vs_west;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The collector: a villager carrying the scoreboard tag {@link #TAG}. Right-clicking one with an item in the
 * main hand registers that item, and never opens its trades. Every other villager is untouched. The tag is
 * vanilla data, so it is saved with the villager and {@code /tag} works on it too.
 */
public final class Collector {

	public static final String TAG = "evw_collector";

	private Collector() {}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			var service = EastVsWest.service();
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || service == null || !service.isRunning() || !isCollector(entity)) {
				return InteractionResult.PASS;
			}
			// Anything but PASS ends the click here: the villager's own right-click, with its trade screen, never runs.
			// Only the main hand registers, so a click isn't counted again for the off hand.
			if (hand == InteractionHand.MAIN_HAND && !serverPlayer.isSpectator()) {
				service.interact(serverPlayer);
			}
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	public static boolean isCollector(Entity entity) {
		return entity instanceof Villager && entity.entityTags().contains(TAG);
	}

	/** Makes a villager a collector, or an ordinary villager again. False if it already was. */
	public static boolean setCollector(Villager villager, boolean collector) {
		return collector ? villager.addTag(TAG) : villager.removeTag(TAG);
	}

	/** A collector that stands where it is put: no AI, no sound, can't be hurt, never despawns. */
	@Nullable
	public static Villager spawn(ServerLevel level, Vec3 pos, float yaw) {
		var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (villager == null) {
			return null;
		}
		villager.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
		villager.setYHeadRot(yaw);
		villager.setYBodyRot(yaw);
		villager.setNoAi(true);
		villager.setSilent(true);
		villager.setPermanentlyInvulnerable(true);
		villager.setPersistenceRequired();
		String name = EvwConfig.get().collectorName();
		if (!name.isEmpty()) {
			villager.setCustomName(Component.literal(name));
			villager.setCustomNameVisible(true);
		}
		villager.addTag(TAG);
		level.addFreshEntity(villager);
		return villager;
	}
}
