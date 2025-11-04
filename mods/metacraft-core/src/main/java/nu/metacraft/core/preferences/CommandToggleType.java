package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;

public class CommandToggleType implements PreferenceType<CommandToggleType.CommandData, Boolean, CommandToggleType.BoolPredicate> {

	@Override
	public MapCodec<CommandData> getDefinitionCodec() {
		return CommandData.CODEC;
	}

	@Override
	public Codec<Boolean> getValueCodec() {
		return Codec.BOOL;
	}

	@Override
	public Codec<BoolPredicate> getValuePredicateCodec() {
		return BoolPredicate.CODEC;
	}


	@Override
	public void onClicked(
			ServerPlayer player, Holder<Preference<CommandData, Boolean, ?>> definition, PreferenceMenu menu
	) {
		var data = PreferenceData.getForPlayer(player);

		boolean newState = !data.get(definition);
		data.set(definition, newState);
		runCommand(player, definition.value().definition().getCommand(newState));

		menu.refreshButtons();
	}

	private void runCommand(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withSuppressedOutput().withPermission(2),
				command
		);
	}

	@Override
	public void initDefaultValue(
			ServerPlayer player,
			Holder<Preference<CommandToggleType.CommandData, Boolean, ?>> definition
	) {
		runCommand(player, definition.value().definition().getCommand(definition.value().defaultValue()));
	}

	public record CommandData(String activationCommand, String deactivationCommand) {

		public static final MapCodec<CommandData> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.STRING.fieldOf("activate").forGetter(CommandData::activationCommand),
						Codec.STRING.fieldOf("deactivate").forGetter(CommandData::deactivationCommand)
				).apply(instance, CommandData::new)
		);

		public String getCommand(boolean activate) {
			return activate ? activationCommand : deactivationCommand;
		}

	}

	public record BoolPredicate(
			boolean v
	) implements Predicate<Boolean> {

		public static final Codec<BoolPredicate> CODEC = Codec.BOOL.xmap(
				BoolPredicate::new, BoolPredicate::v
		);

		@Override
		public boolean test(Boolean i) {
			return v == i;
		}
	}
}
