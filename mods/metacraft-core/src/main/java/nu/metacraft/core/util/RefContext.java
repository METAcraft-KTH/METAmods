package nu.metacraft.core.util;

import java.util.Optional;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public record RefContext(Optional<Entity> entity, ServerLevel world, RandomSource random) {


	public Optional<ServerPlayer> getPlayer() {
		return entity.map(entity -> entity instanceof ServerPlayer p ? p : null);
	}


	public CommandSourceStack getCommandSource() {
		return new CommandSourceStack(
				CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
				world, 2, "RefContext", Component.literal("RefContext"),
				world.getServer(), entity.orElse(null)
		).withSuppressedOutput();
	}

}
