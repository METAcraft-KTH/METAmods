package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.command.BossBarCommand;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import nu.metacraft.core.extensions.CommandBossBarExtension;
import nu.metacraft.core.music.MusicEntry;

@Mixin(BossBarCommand.class)
public abstract class MixinBossBarCommand {

	@Unique
	private static final DynamicCommandExceptionType PASSTHROUGH = new DynamicCommandExceptionType(m -> Text.literal(m.toString()));

	@Shadow
	public static CommandBossBar getBossBar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return null;
	}

	@ModifyExpressionValue(
			method = "register",
			slice = @Slice(
					from = @At(
							value = "CONSTANT",
							args = "stringValue=set",
							ordinal = 0
					)
			),
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;then(Lcom/mojang/brigadier/builder/ArgumentBuilder;)Lcom/mojang/brigadier/builder/ArgumentBuilder;",
					ordinal = 0
			)
	)
	private static ArgumentBuilder<ServerCommandSource, ?> register(
			ArgumentBuilder<ServerCommandSource, ?> argumentBuilder
	) {
		return argumentBuilder.then(
				CommandManager.literal("metacraft.music").then(
						CommandManager.argument("metacraft:music", NbtCompoundArgumentType.nbtCompound()).executes(
								ctx -> {
									var bossBar = (CommandBossBarExtension) getBossBar(ctx);
									var musicData = NbtCompoundArgumentType.getNbtCompound(ctx, "metacraft:music");
									var music = MusicEntry.CODEC.parse(
											ctx.getSource().getRegistryManager().getOps(NbtOps.INSTANCE),
											musicData
									).getOrThrow(PASSTHROUGH::create);
									bossBar.metacraft_core$getMusicHandler().setMusic(music);
									return 1;
								}
						)
				).then(
						CommandManager.literal("none").executes(
								ctx -> {
									var bossBar = (CommandBossBarExtension) getBossBar(ctx);
									bossBar.metacraft_core$getMusicHandler().setMusic(null);
									return 1;
								}
						)
				)
		);
	}

}
