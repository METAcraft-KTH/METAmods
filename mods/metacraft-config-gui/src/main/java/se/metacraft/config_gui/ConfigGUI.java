package se.metacraft.config_gui;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import se.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.config_gui.gui.value_editor.RawInputScreen;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.message.DialogGUIMessages;
import se.metacraft.config_gui.util.EditorGUIPreferences;

import static net.minecraft.commands.Commands.literal;

public class ConfigGUI implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-config-gui");
	public static final String NAMESPACE = METAcraftLib.NAMESPACE;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			ConfigCommand.register(dispatcher, buildContext);
			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				dispatcher.register(
					literal("test-dialog").executes(ctx -> {
						CodecDialog.test(ctx.getSource().getPlayer());
						return 0;
					})
				);
			}
		});
		EditorGUIPreferences.init();
		DialogGUIMessages.init();
		ClickHandlerRegistry.init();
		SubMenu.init();
		RawInputScreen.init();
	}

	public static Identifier getID(String value) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, value);
	}
}
