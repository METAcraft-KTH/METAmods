package nu.metacraft.moderation.moderator_mode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;

import java.util.Optional;

public class ModeratorModeDefinition {

	public static final String NAME = "Name";

	public static final MapCodec<ModeratorModeDefinition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf(NAME).forGetter(ModeratorModeDefinition::getName),
					Codec.BOOL.fieldOf("SeparatePlayerData").forGetter(ModeratorModeDefinition::shouldHaveSeparatePlayerData),
					Codec.STRING.optionalFieldOf("EnterCommand").forGetter(ModeratorModeDefinition::getEnterCommand),
					Codec.STRING.optionalFieldOf("ExitCommand").forGetter(ModeratorModeDefinition::getExitCommand),
					Codec.BOOL.optionalFieldOf("AnnounceAdvancements", true).forGetter(ModeratorModeDefinition::announceAdvancements),
					Codec.BOOL.fieldOf("Vanish").forGetter(d -> d.vanish),
					Codec.BOOL.optionalFieldOf("PreventTamedMobFollow", false).forGetter(d -> d.preventTamedMobFollow)
			).apply(instance, ModeratorModeDefinition::new)
	);

	protected String name;
	protected Optional<String> enterCommand;
	protected Optional<String> exitCommand;
	protected boolean separatePlayerData;
	protected boolean announceAdvancements;
	protected boolean vanish;
	protected boolean preventTamedMobFollow;

	private Runnable markSave = () -> {};

	public ModeratorModeDefinition(
			String name, boolean separatePlayerData, Optional<String> enterCommand, Optional<String> exitCommand, boolean announceAdvancements, boolean vanish, boolean preventTamedMobFollow
	) {
		this.name = name;
		this.separatePlayerData = separatePlayerData;
		this.enterCommand = enterCommand;
		this.exitCommand = exitCommand;
		this.announceAdvancements = announceAdvancements;
		this.vanish = vanish;
		this.preventTamedMobFollow = preventTamedMobFollow;
	}

	public ModeratorModeDefinition(
			String name, boolean separatePlayerData, boolean announceAdvancements, boolean vanish, boolean preventTamedMobFollow
	) {
		this(name, separatePlayerData, Optional.empty(), Optional.empty(), announceAdvancements, vanish, preventTamedMobFollow);
	}

	public void setSave(Runnable markSave) {
		this.markSave = markSave;
	}

	public ModeratorModeDefinition(String name) {
		this(name, false, Optional.empty(), Optional.empty(), true, false, false);
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
		this.enterCommand = Optional.ofNullable(enterCommand);
		markDirty();
	}

	public void setExitCommand(String exitCommand) {
		this.exitCommand = Optional.ofNullable(exitCommand);
		markDirty();
	}

	public void setAnnounceAdvancements(boolean announceAdvancements) {
		this.announceAdvancements = announceAdvancements;
		markDirty();
	}

	public Optional<String> getEnterCommand() {
		return enterCommand;
	}

	public Optional<String> getExitCommand() {
		return exitCommand;
	}

	public boolean shouldHaveSeparatePlayerData() {
		return separatePlayerData;
	}

	public String getName() {
		return name;
	}

	public boolean announceAdvancements() {
		return announceAdvancements;
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
