package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Predicate;

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
			ServerPlayerEntity player, RegistryEntry<Preference<CommandData, Boolean, ?>> definition, PreferenceMenu menu
	) {
		var data = PreferenceData.getForPlayer(player);

		boolean newState = !data.get(definition);
		data.set(definition, newState);
		runCommand(player, definition.value().definition().getCommand(newState));

		menu.refreshButtons();
	}

	private void runCommand(ServerPlayerEntity player, String command) {
		player.getServer().getCommandManager().executeWithPrefix(
				player.getCommandSource().withSilent().withLevel(2),
				command
		);
	}

	@Override
	public void initDefaultValue(
			ServerPlayerEntity player,
			RegistryEntry<Preference<CommandToggleType.CommandData, Boolean, ?>> definition
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
