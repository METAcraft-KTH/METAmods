package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.util.helper.BossBarHelper;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

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
                            try (var logging = LoggingErrorReporter.create(() -> "metacraft:/entity-bossbar", METAcraftCore.LOGGER)) {
                                var writeView = NbtWriteView.create(logging, ctx.getSource().getRegistryManager());
                                entity.writeData(writeView);
                                NbtCompound entityData = writeView.getNbt();
                                var existing = entityData.getCompoundOrEmpty("BossBar");
                                existing.copyFrom(data);
                                entityData.put("BossBar", existing);
                                var readView = NbtReadView.create(logging, ctx.getSource().getRegistryManager(), entityData);
                                BossBarHelper.loadBossBar(entity, readView);
                            }
                            return 0;
                        }
                    )
                ).then(
                    literal("remove").executes(ctx -> {
                        var entity = EntityArgumentType.getEntity(ctx, "entity");
                        BossBarHelper.removeBossBar(entity);
                        return 0;
                    })
                ).then(
                    literal("transfer").then(
                        argument("target", EntityArgumentType.entity()).executes(ctx -> {
                            var source = EntityArgumentType.getEntity(ctx, "entity");
                            var target = EntityArgumentType.getEntity(ctx, "target");
                            if (BossBarHelper.transferBossBar(source, target)) {
                                return 1;
                            } else {
                                ctx.getSource().sendError(Text.literal("The source entity had no bossbar!"));
                                return 0;
                            }
                        })
                    )
                )
            )
        );
    }

}
