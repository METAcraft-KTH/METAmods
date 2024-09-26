package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.util.NoArmorDamageData;

public class NoArmorDamageCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			CommandManager.literal("no-armor-damage")
				.requires(Permissions.require("metacraft.no-armor-damage", 2))
				.executes(ctx -> {
					ServerCommandSource source = ctx.getSource();
					NoArmorDamageData data = NoArmorDamageData.getInstance(source.getServer());
					source.sendMessage(Text.literal(data.isDisableArmorDamage()
						? "Armor damage is currently disabled."
						: "Armor damage is currently active, aka it takes damage. Aka like in vanilla. You understand what I mean."
					));
					return 1;
				})
				.then(
					CommandManager.literal("set")
						.then(
							CommandManager.argument("value", BoolArgumentType.bool())
								.executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									ServerCommandSource source = ctx.getSource();
									NoArmorDamageData data = NoArmorDamageData.getInstance(source.getServer());
									data.setDisableArmorDamage(value);
									source.sendMessage(Text.literal("Value updated."));
									return 1;
								})
						)
				)
		);
	}
}
