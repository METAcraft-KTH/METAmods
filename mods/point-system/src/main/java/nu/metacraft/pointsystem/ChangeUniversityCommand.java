package nu.metacraft.pointsystem;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ChangeUniversityCommand {
    private final PointSystemMod mod;

    public ChangeUniversityCommand(PointSystemMod mod) {
        this.mod = mod;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("change-university")
                .then(
                    argument("codes", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String input = StringArgumentType.getString(ctx, "codes");
                            UUID uuid = ctx.getSource().getPlayerOrException().getUUID();
                            return joinOnlyTeams(ctx, uuid, input);
                        })
                )
        );
    }
    private PointSystem getPointSystem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PointSystem pointSystem = this.mod.getPointSystem(ctx.getSource().getServer());
        if (pointSystem != null) {
            return pointSystem;
        }
        throw PointSystemCommand.NO_POINT_SYSTEM.create();
    }

    private int joinOnlyTeams(CommandContext<CommandSourceStack> ctx, UUID playerUuid, String input) throws CommandSyntaxException {
        String[] codes = input.split(" ");

        PointSystem pointSystem = getPointSystem(ctx);
        pointSystem.joinOnlyTeams(playerUuid, codes);
        return 1;
    }

}
