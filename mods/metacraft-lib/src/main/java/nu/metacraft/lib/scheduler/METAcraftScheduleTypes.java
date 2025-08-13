package nu.metacraft.lib.scheduler;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.timer.TimerCallback;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.TaskScheduler;

public class METAcraftScheduleTypes {

	public static void init() { //Note, Throwaway not being registered is intentional, we don't want the game to load it.
		register("teleport_player", TeleportPlayer.CODEC);
	}

	private static void register(String id, MapCodec<? extends TimerCallback<MinecraftServer>> codec) {
		TaskScheduler.registerTaskType(
				METAcraftLib.getID(id), codec
		);
	}

}
