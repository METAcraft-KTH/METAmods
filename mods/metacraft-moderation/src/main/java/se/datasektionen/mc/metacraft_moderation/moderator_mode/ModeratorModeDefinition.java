package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.Optional;

public class ModeratorModeDefinition {

	private static final String NAME = "Name";
	private static final String ENTER_COMMAND = "EnterCommand";
	private static final String EXIT_COMMAND = "ExitCommand";
	private static final String SEPARATE_PLAYER_DATA = "SeparatePlayerData";
	private static final String VANISH = "Vanish";
	private static final String PREVENT_TAMED_MOB_FOLLOW = "PreventTamedMobFollow";

	protected String name;
	protected String enterCommand;
	protected String exitCommand;
	protected boolean separatePlayerData;
	protected boolean vanish;
	protected boolean preventTamedMobFollow;

	private Runnable markSave = () -> {};

	public ModeratorModeDefinition(
			String name, boolean separatePlayerData, boolean vanish, boolean preventTamedMobFollow
	) {
		this.name = name;
		this.separatePlayerData = separatePlayerData;
		this.vanish = vanish;
		this.preventTamedMobFollow = preventTamedMobFollow;
	}

	public void setSave(Runnable markSave) {
		this.markSave = markSave;
	}

	public ModeratorModeDefinition(String name) {
		this.name = name;
	}

	public void setVanish(boolean vanish) {
		this.vanish = vanish;
		markDirty();
	}

	public void setSeparatePlayerData(boolean separatePlayerData) {
		this.separatePlayerData = separatePlayerData;
		markDirty();
	}

	public void setPreventTamedMobFollow(boolean preventTamedAnimalFollow) {
		this.preventTamedMobFollow = preventTamedAnimalFollow;
		markDirty();
	}

	public boolean preventTamedMobFollow() {
		return preventTamedMobFollow;
	}

	public void setEnterCommand(String enterCommand) {
		this.enterCommand = enterCommand;
		markDirty();
	}

	public void setExitCommand(String exitCommand) {
		this.exitCommand = exitCommand;
		markDirty();
	}

	public Optional<String> getEnterCommand() {
		return Optional.ofNullable(enterCommand);
	}

	public Optional<String> getExitCommand() {
		return Optional.ofNullable(exitCommand);
	}

	public boolean shouldHaveSeparatePlayerData() {
		return separatePlayerData;
	}

	public String getName() {
		return name;
	}

	public NbtCompound toNBT() {
		NbtCompound nbt = new NbtCompound();

		nbt.putString(NAME, name);

		if (enterCommand != null) {
			nbt.putString(ENTER_COMMAND, enterCommand);
		}
		if (exitCommand != null) {
			nbt.putString(EXIT_COMMAND, exitCommand);
		}

		nbt.putBoolean(SEPARATE_PLAYER_DATA, separatePlayerData);
		nbt.putBoolean(VANISH, vanish);
		nbt.putBoolean(PREVENT_TAMED_MOB_FOLLOW, preventTamedMobFollow);

		return nbt;
	}

	public void fromNBT(NbtCompound nbt) {
		name = nbt.getString(NAME);

		if (nbt.contains(ENTER_COMMAND)) {
			enterCommand = nbt.getString(ENTER_COMMAND);
		}
		if (nbt.contains(EXIT_COMMAND)) {
			exitCommand = nbt.getString(EXIT_COMMAND);
		}

		separatePlayerData = nbt.getBoolean(SEPARATE_PLAYER_DATA);

		vanish = nbt.getBoolean(VANISH);

		preventTamedMobFollow = nbt.getBoolean(PREVENT_TAMED_MOB_FOLLOW);
	}

	public Text toText() {
		String builder = "Name: " + name + "\n" +
				"SeparatePlayerData: " + separatePlayerData + "\n" +
				"Vanish: " + vanish + "\n" +
				"PreventTamedAnimalFollow: " + preventTamedMobFollow + "\n" +
				"EnterCommand: " + (enterCommand != null ? enterCommand : "None") + "\n" +
				"ExitCommand: " + (exitCommand != null ? exitCommand : "None") + "\n";
		return Text.literal(builder);
	}

	public void markDirty() {
		this.markSave.run();
	}

}
