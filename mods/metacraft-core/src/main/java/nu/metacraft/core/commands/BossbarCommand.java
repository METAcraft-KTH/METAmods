package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.util.helper.BossBarHelper;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BossbarCommand {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("entity-bossbar").requires(Permissions.require("metacraft.entity-bossbar", 2)).then(
				argument("entity", EntityArgument.entity()).then(
					argument("data", CompoundTagArgument.compoundTag()).executes(
						ctx -> {
							var entity = EntityArgument.getEntity(ctx, "entity");
							var data = CompoundTagArgument.getCompoundTag(ctx, "data");
							try (var logging = LoggingErrorReporter.create(() -> "metacraft:/entity-bossbar", METAcraftCore.LOGGER)) {
								var writeView = TagValueOutput.createWithContext(logging, ctx.getSource().registryAccess());
								entity.saveWithoutId(writeView);
								CompoundTag entityData = writeView.buildResult();
								var existing = entityData.getCompoundOrEmpty("BossBar");
								existing.merge(data);
								entityData.put("BossBar", existing);
								var readView = TagValueInput.create(logging, ctx.getSource().registryAccess(), entityData);
								BossBarHelper.loadBossBar(entity, readView);
							}
							return 0;
						}
					)
				).then(
					literal("remove").executes(ctx -> {
						var entity = EntityArgument.getEntity(ctx, "entity");
						BossBarHelper.removeBossBar(entity);
						return 0;
					})
				).then(
					literal("transfer").then(
						argument("target", EntityArgument.entity()).executes(ctx -> {
							var source = EntityArgument.getEntity(ctx, "entity");
							var target = EntityArgument.getEntity(ctx, "target");
							if (BossBarHelper.transferBossBar(source, target)) {
								return 1;
							} else {
								ctx.getSource().sendFailure(Component.literal("The source entity had no bossbar!"));
								return 0;
							}
						})
					)
				)
			)
		);
	}

}
