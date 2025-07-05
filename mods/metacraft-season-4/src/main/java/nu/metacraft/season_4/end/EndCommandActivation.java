package nu.metacraft.season_4.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import nu.metacraft.core.METAcraftCore;

import java.util.Optional;

public class EndCommandActivation extends PersistentState {

	private static final PersistentStateType<EndCommandActivation> TYPE = new PersistentStateType<>(
			METAcraftCore.NAMESPACE + "-end-command-activation",
			EndCommandActivation::new, RecordCodecBuilder.create(
					instance -> instance.group(
							Codec.STRING.optionalFieldOf("command").forGetter(t -> t.command)
					).apply(instance, EndCommandActivation::new)
			), null
	);

	private Optional<String> command = Optional.empty();


	public EndCommandActivation() {

	}

	public EndCommandActivation(Optional<String> command) {
		this.command = command;
	}

	public static EndCommandActivation getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
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
}
