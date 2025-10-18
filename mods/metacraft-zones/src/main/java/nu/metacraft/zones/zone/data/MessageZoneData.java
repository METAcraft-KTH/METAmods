package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

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

	private ServerCommandSource createFromPlayer(ServerPlayerEntity player) {
		return player.getCommandSource().withLevel(2).withSilent();
	}

	@Override
	public void onEnter(Entity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			enterCommand.ifPresent(cmd -> {
				player.getEntityWorld().getServer().getCommandManager().parseAndExecute(
						createFromPlayer(player), cmd
				);
			});
		}
	}

	@Override
	public void onLeave(Entity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			leaveCommand.ifPresent(cmd -> {
				player.getEntityWorld().getServer().getCommandManager().parseAndExecute(
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
