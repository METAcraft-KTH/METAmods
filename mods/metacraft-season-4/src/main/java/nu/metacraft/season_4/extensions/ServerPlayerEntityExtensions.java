package nu.metacraft.season_4.extensions;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface ServerPlayerEntityExtensions {

	@Nullable
	RegistryKey<World> metacraft_core$getCampusLodestoneBackWorld();

	@Nullable
	BlockPos metacraft_core$getCampusLodestoneBackPos();

	void metacraft_core$setCampusLodestoneBackPos(RegistryKey<World> world, BlockPos pos);

	void metacraft_core$unsetCampusLodestoneBackPos();

	void metacraft_season_4$setAttackedThroughFriendlyFire(boolean state);
}
