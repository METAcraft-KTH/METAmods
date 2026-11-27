package se.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import se.metacraft.portable_jukebox.data.HasBoombox;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements HasBoombox {

	@Unique
	private final Set<BoomboxEntity> boomboxes = new HashSet<>();

	@Override
	public void portable_jukebox$addBoombox(BoomboxEntity entity) {
		this.boomboxes.add(entity);
	}

	@Override
	public void portable_jukebox$removeBoombox(BoomboxEntity entity) {
		boomboxes.remove(entity);
	}

	@Override
	public Set<BoomboxEntity> portable_jukebox$getBoomboxes() {
		return boomboxes;
	}
}
