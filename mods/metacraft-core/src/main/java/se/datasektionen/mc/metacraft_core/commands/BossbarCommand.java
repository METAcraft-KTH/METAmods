package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BossbarCommand {

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess
    ) {
        dispatcher.register(
            literal("entity-bossbar").requires(Permissions.require("metacraft.entity-bossbar", 2)).then(
                argument("entity", EntityArgumentType.entity()).then(
                    argument("data", NbtCompoundArgumentType.nbtCompound()).executes(
                        ctx -> {
                            var entity = EntityArgumentType.getEntity(ctx, "entity");
                            var data = NbtCompoundArgumentType.getNbtCompound(ctx, "data");
                            var entityData = entity.writeNbt(new NbtCompound());
                            var existing = entityData.getCompound("BossBar");
                            existing.copyFrom(data);
                            entityData.put("BossBar", existing);
                            ((EntityExtensions) entity).metacraft_lib$loadBossBar(entityData);
                            return 0;
                        }
                    )
                ).then(
                    literal("remove").executes(ctx -> {
                        var entity = EntityArgumentType.getEntity(ctx, "entity");
                        ((EntityExtensions) entity).metacraft_lib$loadBossBar(new NbtCompound());
                        return 0;
                    })
                )
            )
        );
    }

}
