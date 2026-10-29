package se.metacraft.info_commands.command_action;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.apache.commons.lang3.mutable.MutableInt;
import se.metacraft.config.util.CommentCodec;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public record CommandCommandAction(String command, Optional<PermissionLevel> permissionLevel) implements CommandAction {

	public static final MapCodec<CommandCommandAction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			CommentCodec.comment(
				Codec.STRING.fieldOf("command"),
				"The command to run"
			).forGetter(CommandCommandAction::command),
			CommentCodec.comment(
				Codec.withAlternative(
					PermissionLevel.CODEC, PermissionLevel.INT_CODEC
				).optionalFieldOf("permission_level"),
				"The permission level to run the command with\nValid values: " +
					Arrays.stream(PermissionLevel.values()).map(
						level -> level.getSerializedName() + " or " + level.id()
					).collect(Collectors.joining(", "))
			).forGetter(CommandCommandAction::permissionLevel)
		).apply(instance, CommandCommandAction::new)
	);

	@Override
	public MapCodec<? extends CommandAction> codec() {
		return CODEC;
	}

	@Override
	public int run(CommandContext<CommandSourceStack> ctx) {
		MutableInt r = new MutableInt(0);
		var newSource = ctx.getSource().withCallback((success, result) -> r.setValue(result));
		if (permissionLevel.isPresent()) {
			newSource = newSource.withPermission(LevelBasedPermissionSet.forLevel(permissionLevel.get()));
		}
		ctx.getSource().getServer().getCommands().performPrefixedCommand(
			newSource,
			this.command
		);
		return r.intValue();
	}
}
