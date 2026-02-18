package nu.metacraft.cutscenes.cutscene.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.clock.PackedClockStates;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.mixin.ServerClockManagerAccessor;
import org.jspecify.annotations.NonNull;

public class CutsceneClockManager extends ServerClockManager {

	public static final SavedDataType<CutsceneClockManager> TYPE = new SavedDataType<>(
			Cutscenes.getID("world_clocks"),
			() -> new CutsceneClockManager(PackedClockStates.EMPTY),
			PackedClockStates.CODEC.xmap(CutsceneClockManager::new, CutsceneClockManager::packState),
			DataFixTypes.SAVED_DATA_WORLD_CLOCKS
	);

	private CutsceneLevel level;

	public CutsceneClockManager(PackedClockStates empty) {
		super(empty);
	}

	public CutsceneLevel getLevel() {
		return level;
	}

	@Deprecated
	public void init(@NonNull MinecraftServer server) {
		super.init(server);
	}

	public void init(final CutsceneLevel level) {
		init(level.getServer());
		this.level = level;
	}

	public void copyFrom(ServerClockManager source) {
		((ServerClockManagerAccessor) this).setPackedClockStates(source.packState());
	}

	@Override
	public void tick() {
		boolean advanceTime = level.getGameRules().get(GameRules.ADVANCE_TIME);
		if (advanceTime) {
			((ServerClockManagerAccessor) this).getClocks().values().forEach(ClockInstance::tick);
			this.setDirty();
		}

	}
}
