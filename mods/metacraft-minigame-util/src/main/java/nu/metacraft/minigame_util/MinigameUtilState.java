package nu.metacraft.minigame_util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.METAcraftLib;

import java.util.Optional;

public class MinigameUtilState extends SavedData {

	private static final Identifier KEY = METAcraftLib.getID("minigame-util");

	private BlockPredicateList canBreak;

	private static final Codec<MinigameUtilState> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			BlockPredicateList.CODEC.optionalFieldOf("canBreak").forGetter(state -> Optional.ofNullable(state.canBreak))
		).apply(instance, MinigameUtilState::new)
	);

	private static final SavedDataType<MinigameUtilState> TYPE = new SavedDataType<>(
		KEY, MinigameUtilState::new, CODEC, null
	);

	private MinigameUtilState() {}

	public MinigameUtilState(Optional<BlockPredicateList> list) {
		this.canBreak = list.orElse(null);
	}

	public static MinigameUtilState getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void setCanBreak(BlockPredicateList canBreak) {
		this.canBreak = canBreak;
		setDirty();
	}

	public TriState canBreak(ServerLevel world, BlockPos pos) {
		if (canBreak != null) {
			return TriState.of(canBreak.test(world, pos));
		} else {
			return TriState.DEFAULT;
		}
	}

}
