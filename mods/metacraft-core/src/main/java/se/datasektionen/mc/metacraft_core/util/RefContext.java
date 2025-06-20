package se.datasektionen.mc.metacraft_core.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.Optional;

public class RefContext {

	protected final ServerWorld world;
	protected final Optional<Entity> entity;
	protected final Random random;

	public RefContext(Optional<Entity> entity, ServerWorld world, Random random) {
		this.world = world;
		this.entity = entity;
		this.random = random;
	}

	public Optional<Entity> getEntity() {
		return entity;
	}

	public Optional<ServerPlayerEntity> getPlayer() {
		return entity.map(entity -> entity instanceof ServerPlayerEntity p ? p : null);
	}

	public Random getRandom() {
		return random;
	}

	public ServerWorld getWorld() {
		return world;
	}

	public ServerCommandSource getCommandSource() {
		return new ServerCommandSource(
				CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO,
				world, 2, "RefContext", Text.literal("RefContext"),
				world.getServer(), entity.orElse(null)
		).withSilent();
	}

}
