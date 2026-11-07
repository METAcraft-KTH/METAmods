package se.metacraft.fake_players;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** A connection that discards everything sent to it, so an EmbeddedChannel does not accumulate packets forever. */
public class FakeConnection extends Connection {
	public FakeConnection() {
		super(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(this);
	}

	@Override
	public void send(Packet<?> packet) {
	}

	@Override
	public void send(Packet<?> packet, ChannelFutureListener listener) {
	}

	@Override
	public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
	}

	@Override
	public void flushChannel() {
	}
}
