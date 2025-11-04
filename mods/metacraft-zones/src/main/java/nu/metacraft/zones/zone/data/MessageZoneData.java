package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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

	private CommandSourceStack createFromPlayer(ServerPlayer player) {
		return player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
	}

	@Override
	public void onEnter(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			enterCommand.ifPresent(cmd -> {
				player.level().getServer().getCommands().performPrefixedCommand(
						createFromPlayer(player), cmd
				);
			});
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			leaveCommand.ifPresent(cmd -> {
				player.level().getServer().getCommands().performPrefixedCommand(
						createFromPlayer(player), cmd
				);
			});
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
}
