package nu.metacraft.pointsystem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public class ChangeUniversityCommand {
    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("change-university")
                .executes(this::run)
        );
    }

    private int run(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        var manager = source.getServer().getCommandFunctionManager();
        var function = manager.getFunction(Identifier.of("score", "change_university"));
        if (function.isEmpty()) {
            source.sendError(Text.literal("No function found."));
            return 0;
        }
        manager.execute(function.get(), source.withLevel(2).withSilent());
        return 1;
    }

}
