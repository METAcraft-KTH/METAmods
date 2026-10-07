package se.metacraft.config_gui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import nu.metacraft.lib.METAcraftLib;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config_gui.gui.value_editor.ConfirmScreen;
import se.metacraft.config_gui.gui.value_editor.ValueEditor;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.message.DialogGUIHandler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ConfigCommand {

	public static Stream<? extends ConfigContainer<?>> getContainers() {
		return ConfigContainer.CONFIG_FINDER.invoker().containers().filter(c -> c.name().isPresent());
	}

	private static final SuggestionProvider<CommandSourceStack> SUGGEST_CONFIGS = (context, builder) -> SharedSuggestionProvider.suggest(
		getContainers().map(ConfigContainer::name).map(Optional::orElseThrow), builder
	);

	private static <T> void openScreen(ConfigContainer<T> config, ServerPlayer player, HolderLookup.Provider lookup) {
		var confirmation = new ConfirmScreen.Editor<>(
			List.of(new PlainMessage(Component.literal("Would you like to save your changes?"), 200)),
			null,
			gui -> {
				if (gui instanceof ValueEditor<?> editor) {
					var value = editor.getValue(lookup);
					if (value.isEmpty()) return null;
					config.modify(c -> {
						try {
							//noinspection unchecked
							return (T) value.get();
						} catch (ClassCastException err) {
							return c;
						}
					});
				}
				return null;
			},
			Optional.empty()
		);
		DialogGUIHandler.openGUI(
			player,
			SubMenu.open(
				config.get(), CodecDialog.Type.from(CodecParser.parse(config.codec(), lookup)),
				lookup, confirmation, new SubMenu.S("root")
			)
		);
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(
			literal("meta-config-screen").requires(PermissionPredicates.require(METAcraftLib.getID("config.screen"), PermissionLevel.ADMINS)).then(
				argument("name", StringArgumentType.word()).suggests(SUGGEST_CONFIGS).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					var config = getContainers().filter(c -> c.name().orElseThrow().equals(name)).findAny();
					if (config.isPresent()) {
						openScreen(config.get(), ctx.getSource().getPlayerOrException(), ctx.getSource().registryAccess());
						return 1;
					}
					return 0;
				})
			)
		);
	}

}
