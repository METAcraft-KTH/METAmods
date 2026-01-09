package nu.metacraft.moderation.moderator_mode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import nu.metacraft.lib.util.METACodecs;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ModeratorModeDefinition {

	public static final String NAME = "name";

	protected static final MapCodec<ModeratorModeDefinition> MODERN_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf(NAME).forGetter(ModeratorModeDefinition::getName),
					Codec.BOOL.fieldOf("separate_player_data").forGetter(ModeratorModeDefinition::shouldHaveSeparatePlayerData),
					Codec.STRING.optionalFieldOf("enter_command").forGetter(ModeratorModeDefinition::getEnterCommand),
					Codec.STRING.optionalFieldOf("exit_command").forGetter(ModeratorModeDefinition::getExitCommand),
					Codec.BOOL.optionalFieldOf("announce_advancements", true).forGetter(ModeratorModeDefinition::announceAdvancements),
					Codec.BOOL.fieldOf("vanish").forGetter(d -> d.vanish),
					Codec.BOOL.optionalFieldOf("followed_by_tamed_mobs", true).forGetter(d -> d.followedByTamedMobs)
			).apply(instance, ModeratorModeDefinition::new)
	);

	private static final MapCodec<ModeratorModeDefinition> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("Name").forGetter(ModeratorModeDefinition::getName),
					Codec.BOOL.fieldOf("SeparatePlayerData").forGetter(ModeratorModeDefinition::shouldHaveSeparatePlayerData),
					Codec.STRING.optionalFieldOf("EnterCommand").forGetter(ModeratorModeDefinition::getEnterCommand),
					Codec.STRING.optionalFieldOf("ExitCommand").forGetter(ModeratorModeDefinition::getExitCommand),
					Codec.BOOL.optionalFieldOf("AnnounceAdvancements", true).forGetter(ModeratorModeDefinition::announceAdvancements),
					Codec.BOOL.fieldOf("Vanish").forGetter(d -> d.vanish),
					Codec.BOOL.optionalFieldOf("PreventTamedMobFollow", false).forGetter(d -> !d.followedByTamedMobs)
			).apply(
					instance,
					(name, data, enter, exit, adv, vanish, preventTame) -> new ModeratorModeDefinition(
							name, data, enter, exit, adv, vanish, !preventTame
					)
			)
	);

	public static final MapCodec<ModeratorModeDefinition> CODEC = METACodecs.withAlternative(
			MODERN_CODEC, LEGACY_CODEC
	);

	protected String name;
	protected Optional<String> enterCommand;
	protected Optional<String> exitCommand;
	protected boolean separatePlayerData;
	protected boolean announceAdvancements;
	protected boolean vanish;
	protected boolean followedByTamedMobs;

	private Runnable markSave = () -> {};

	public ModeratorModeDefinition(
			String name, boolean separatePlayerData, Optional<String> enterCommand, Optional<String> exitCommand, boolean announceAdvancements, boolean vanish, boolean followedByTamedMobs
	) {
		this.name = name;
		this.separatePlayerData = separatePlayerData;
		this.enterCommand = enterCommand;
		this.exitCommand = exitCommand;
		this.announceAdvancements = announceAdvancements;
		this.vanish = vanish;
		this.followedByTamedMobs = followedByTamedMobs;
	}

	public ModeratorModeDefinition(
			String name, boolean separatePlayerData, boolean announceAdvancements, boolean vanish, boolean followedByTamedMobs
	) {
		this(name, separatePlayerData, Optional.empty(), Optional.empty(), announceAdvancements, vanish, followedByTamedMobs);
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

	public void setFollowedByTamedMobs(boolean preventTamedAnimalFollow) {
		this.followedByTamedMobs = preventTamedAnimalFollow;
		markDirty();
	}

	public boolean followedByTamedMobs() {
		return followedByTamedMobs;
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

	public Component toText() {
		String builder = "Name: " + name + "\n" +
				"SeparatePlayerData: " + separatePlayerData + "\n" +
				"Vanish: " + vanish + "\n" +
				"FollowedByTamedMobs: " + followedByTamedMobs + "\n" +
				"EnterCommand: " + (enterCommand != null ? enterCommand : "None") + "\n" +
				"ExitCommand: " + (exitCommand != null ? exitCommand : "None") + "\n";
		return Component.literal(builder);
	}

	public void markDirty() {
		this.markSave.run();
	}

}
