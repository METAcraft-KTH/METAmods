package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import nu.metacraft.zones.PlayerZoneMessageExtension;
import org.jetbrains.annotations.Nullable;

public class MessageZoneData extends ZoneDataEntityTracking {

	public static final MapCodec<MessageZoneData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.optionalFieldOf("enterCommand").forGetter(data -> data.enterCommand),
					Codec.STRING.optionalFieldOf("leaveCommand").forGetter(data -> data.leaveCommand)
			).apply(instance, MessageZoneData::new)
	);

	protected Optional<String> enterCommand;
	protected Optional<String> leaveCommand;

	public MessageZoneData(Optional<String> enterCommand, Optional<String> leaveCommand) {
		this.enterCommand = enterCommand;
		this.leaveCommand = leaveCommand;
	}

	private static CommandSourceStack createFromPlayer(ServerPlayer player) {
		return player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput();
	}

	private boolean matches(@Nullable MessageEntry entry, MessageEntry.Type type) {
		return entry != null && entry.type == type;
	}

	@Override
	public void onEnter(Entity entity) {
		if (enterCommand.isEmpty() && leaveCommand.isEmpty()) return;
		if (entity instanceof PlayerZoneMessageExtension player) {
			if (matches(player.metacraft$getZoneMessage(zone), MessageEntry.Type.EXIT)) {
				player.metacraft$removeZoneMessage(zone);
			} else {
				player.metacraft$addZoneMessage(
						zone, MessageEntry.create(entity.level().getServer(), 50, enterCommand, MessageEntry.Type.ENTRY)
				);
			}
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (enterCommand.isEmpty() && leaveCommand.isEmpty()) return;
		if (entity instanceof PlayerZoneMessageExtension player) {
			if (matches(player.metacraft$getZoneMessage(zone), MessageEntry.Type.ENTRY)) {
				player.metacraft$removeZoneMessage(zone);
			} else {
				player.metacraft$addZoneMessage(
						zone, MessageEntry.create(entity.level().getServer(), 50, leaveCommand, MessageEntry.Type.EXIT)
				);
			}
		}
	}

	public void setEnterCommand(Optional<String> cmd) {
		enterCommand = cmd;
		markDirty();
	}

	public void setLeaveCommand(Optional<String> cmd) {
		leaveCommand = cmd;
		markDirty();
	}

	public Optional<String> getEnterCommand() {
		return enterCommand;
	}

	public Optional<String> getLeaveCommand() {
		return leaveCommand;
	}

	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return ZoneDataRegistry.MESSAGE;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder("MessageZoneData[");
		enterCommand.ifPresent(cmd -> {
			text.append("enterCommand=").append(cmd).append(",");
		});
		leaveCommand.ifPresent(cmd -> {
			text.append("exitCommand=").append(cmd).append(",");
		});
		text.replace(text.length()-1, text.length(), "]");
		return text.toString();
	}

	public record MessageEntry(int time, Optional<String> command, Type type) {

		public boolean tryRunCommand(ServerPlayer player) {
			if (player.level().getServer().getTickCount() >= time) {
				command.ifPresent(cmd -> {
					player.level().getServer().getCommands().performPrefixedCommand(
							createFromPlayer(player), cmd
					);
				});
				return true;
			}
			return false;
		}

		public static MessageEntry create(MinecraftServer server, int delay, Optional<String> command, Type type) {
			return new MessageEntry(server.getTickCount() + delay, command, type);
		}

		public enum Type {
			ENTRY, EXIT
		}
	}
}
