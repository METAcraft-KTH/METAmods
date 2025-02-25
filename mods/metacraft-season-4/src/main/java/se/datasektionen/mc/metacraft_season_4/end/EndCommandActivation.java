package se.datasektionen.mc.metacraft_season_4.end;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.Optional;

public class EndCommandActivation extends PersistentState {
	private static final String KEY = METAcraftCore.NAMESPACE + "-end-command-activation";
	private static final String COMMAND = "command";

	private static final Type<EndCommandActivation> TYPE = new Type<>(EndCommandActivation::new, EndCommandActivation::fromNBT, null);

	private static EndCommandActivation fromNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var data = new EndCommandActivation();
		data.readNBT(nbt, lookup);
		return data;
	}

	private Optional<String> command = Optional.empty();


	public EndCommandActivation() {

	}

	public static EndCommandActivation getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	public void setCommand(String command) {
		this.command = Optional.ofNullable(command);
		markDirty();
	}

	public void removeCommand() {
		command = Optional.empty();
		markDirty();
	}

	public Optional<String> getCommand() {
		return command;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		command.ifPresent(c -> nbt.putString(COMMAND, c));
		return nbt;
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		if (nbt.contains(COMMAND)) {
			command = Optional.of(nbt.getString(COMMAND));
		} else {
			command = Optional.empty();
		}
	}
}
