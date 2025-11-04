package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.BossBarCommands;
import nu.metacraft.core.music.PlayerMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import nu.metacraft.core.extensions.CommandBossBarExtension;

@Mixin(BossBarCommands.class)
public abstract class MixinBossBarCommand {

	@Unique
	private static final DynamicCommandExceptionType PASSTHROUGH = new DynamicCommandExceptionType(m -> Component.literal(m.toString()));

	@Shadow
	public static CustomBossEvent getBossBar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
	private static ArgumentBuilder<CommandSourceStack, ?> register(
			ArgumentBuilder<CommandSourceStack, ?> argumentBuilder
	) {
		return argumentBuilder.then(
				Commands.literal("metacraft.music").then(
						Commands.argument("metacraft:music", CompoundTagArgument.compoundTag()).executes(
								ctx -> {
									var bossBar = (CommandBossBarExtension) getBossBar(ctx);
									var musicData = CompoundTagArgument.getCompoundTag(ctx, "metacraft:music");
									var music = PlayerMusic.EASY_CODEC.parse(
											ctx.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE),
											musicData
									).getOrThrow(PASSTHROUGH::create);
									bossBar.metacraft_core$getMusicHandler().setMusic(music);
									return 1;
								}
						)
				).then(
						Commands.literal("none").executes(
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
