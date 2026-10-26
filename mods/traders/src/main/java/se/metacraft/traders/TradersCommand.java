package se.metacraft.traders;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

/**
 * {@code /traders spawn}: all three traders in a row in front of you, the outer two turned in to the middle one, as if
 * all three were looking at one person standing in front of them.
 */
public final class TradersCommand {

	/** Blocks between the traders, and from the player to the middle one. */
	private static final double SPACING = 1.5;
	private static final double DISTANCE = 2.5;
	/** How far in front of the middle trader the imagined person they look at stands. */
	private static final double FOCUS_DISTANCE = 4.0;

	private TradersCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				literal("traders").requires(PermissionPredicates.require(Traders.getID("traders"), PermissionLevel.GAMEMASTERS))
						.then(literal("spawn").executes(ctx -> spawnAll(ctx.getSource())))
		);
	}

	private static int spawnAll(CommandSourceStack source) {
		var level = source.getLevel();
		float yaw = source.getRotation().y;
		// the horizontal direction the player looks, and the one to its right
		double rad = Math.toRadians(yaw);
		var forward = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		var right = new Vec3(-forward.z, 0, forward.x);
		var middle = source.getPosition().add(forward.scale(DISTANCE));
		var focus = middle.subtract(forward.scale(FOCUS_DISTANCE));

		List<EntityType<TraderEntity>> types = List.of(TraderEntities.DATA, TraderEntities.IT, TraderEntities.MEDIA);
		for (int i = 0; i < types.size(); i++) {
			var pos = middle.add(right.scale((i - 1) * SPACING));
			var trader = types.get(i).create(level, EntitySpawnReason.COMMAND);
			if (trader == null) {
				continue;
			}
			// a yaw of 0 faces south (+z), 90 faces west (-x)
			float facing = (float) Math.toDegrees(Math.atan2(pos.x - focus.x, focus.z - pos.z));
			trader.snapTo(pos.x, pos.y, pos.z, facing, 0f);
			trader.setYHeadRot(facing);
			trader.setYBodyRot(facing);
			level.addFreshEntity(trader);
		}
		source.sendSuccess(() -> Component.literal("Spawned the data, IT and media traders"), true);
		return types.size();
	}
}
