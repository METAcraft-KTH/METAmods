package nu.metacraft.portal_blocker;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;

import java.util.Locale;

public class PortalState {

	private byte state;

	private static final byte CREATION = 0;
	private static final byte TRAVEL = 1;

	public static final Codec<PortalState> CODEC = Codec.BYTE.xmap(
			PortalState::new, s -> s.state
	);

	private PortalState(byte v) {
		this.state = v;
	}

	public PortalState(BlockingType... values) {
		for (BlockingType type : values) {
			setBlocked(type, true);
		}
	}

	private void set(boolean value, byte pos) {
		if (value) {
			state |= (byte) ((byte) 1 << pos);
		} else {
			state &= (byte) ~(1 << pos);
		}
	}

	private boolean get(byte pos) {
		return (state & 1 << pos) != 0;
	}

	public void setBlocked(BlockingType type, boolean value) {
		set(value, type.pos);
	}

	public boolean isBlocked(BlockingType type) {
		return get(type.pos);
	}

	public NbtByte toNBT() {
		return NbtByte.of(state);
	}

	public PortalState fromNBT(NbtElement element) {
		if (element instanceof AbstractNbtNumber num) {
			state = num.byteValue();
		} else {
			PortalBlocker.LOGGER.error("Invalid number for PortalState in NBT.");
		}
		return this;
	}

	@Override
	public String toString() {
		return "PortalState[creation=" + Commands.getBlockStateText(get(CREATION)) + ", travel=" + Commands.getBlockStateText(get(TRAVEL)) + "]";
	}

	public enum BlockingType {
		CREATION(PortalState.CREATION),
		TRAVEL(PortalState.TRAVEL);

		private final byte pos;

		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}

		BlockingType(byte pos) {
			this.pos = pos;
		}
	}

}
