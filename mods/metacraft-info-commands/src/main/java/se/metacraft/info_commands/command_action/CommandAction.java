package se.metacraft.info_commands.command_action;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentSerialization;

public interface CommandAction {

	MapCodec<CommandAction> REGISTRY_CODEC = ComponentSerialization.createLegacyComponentMatcher(
		CommandActionRegistry.ID_MAPPER,
		CommandAction::codec,
		"type"
	);

	MapCodec<? extends CommandAction> codec();

	int run(CommandContext<CommandSourceStack> ctx);

}
