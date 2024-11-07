package nu.metacraft.minigame_util;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

public class MinigameUtilState extends PersistentState {

	private static final String KEY = "metacraft-minigame-util";

	private static final String BLOCK_SETTING_RULES = "BlockSettingRules";

	private BlockPredicateList canBreak;

	private static final PersistentState.Type<MinigameUtilState> TYPE = new Type<>(MinigameUtilState::new, MinigameUtilState::fromNBT, null);

	private static MinigameUtilState fromNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		var data = new MinigameUtilState();
		data.readNBT(nbt, registryLookup);
		return data;
	}

	public static MinigameUtilState getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		if (canBreak != null) {
			BlockPredicateList.CODEC.encodeStart(
					registries.getOps(NbtOps.INSTANCE),
					canBreak
			).resultOrPartial(MinigameUtil.LOGGER::error).ifPresent(result -> {
				nbt.put(BLOCK_SETTING_RULES, result);
			});
		}
		return nbt;
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		if (nbt.contains(BLOCK_SETTING_RULES)) {
			BlockPredicateList.CODEC.parse(
					registries.getOps(NbtOps.INSTANCE),
					nbt.getCompound(BLOCK_SETTING_RULES)
			).resultOrPartial(MinigameUtil.LOGGER::error).ifPresent(result -> {
				canBreak = result;
			});
		}
	}

	public void setCanBreak(BlockPredicateList canBreak) {
		this.canBreak = canBreak;
		markDirty();
	}

	public TriState canBreak(ServerWorld world, BlockPos pos) {
		if (canBreak != null) {
			return TriState.of(canBreak.test(world, pos));
		} else {
			return TriState.DEFAULT;
		}
	}

}
