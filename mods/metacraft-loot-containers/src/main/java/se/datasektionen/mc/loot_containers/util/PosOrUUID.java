package se.datasektionen.mc.loot_containers.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class PosOrUUID {

	private final Either<BlockPos, UUID> pos;

	public PosOrUUID(BlockPos pos) {
		this.pos = Either.left(pos);
	}

	public PosOrUUID(UUID uuid) {
		this.pos = Either.right(uuid);
	}

	public Either<BlockPos, UUID> getPos() {
		return pos;
	}

	@Override
	public String toString() {
		return pos.map(Object::toString, Object::toString);
	}

}
