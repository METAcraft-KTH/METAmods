package nu.metacraft.repair_fix;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RepairFix implements ModInitializer {

	public static final String modid = "metacraft_repair_fix";
	private static final Logger logger = LogManager.getLogger(modid);

	public static Logger getLogger() {
		return logger;
	}


	@Override
	public void onInitialize() {
		logger.info("Loaded Repair Fix by Acuadragon100");
		RepairFixConfig.init();
	}
}
