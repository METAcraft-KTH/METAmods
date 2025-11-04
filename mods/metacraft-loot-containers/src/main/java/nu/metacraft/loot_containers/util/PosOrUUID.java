package nu.metacraft.loot_containers.util;

import com.mojang.datafixers.util.Either;
import java.util.UUID;
import net.minecraft.core.BlockPos;

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
