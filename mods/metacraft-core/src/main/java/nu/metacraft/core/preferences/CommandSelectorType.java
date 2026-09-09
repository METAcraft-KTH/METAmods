package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.sgui.api.elements.GuiElement;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import nu.metacraft.lib.util.DisplayItemData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;

public class CommandSelectorType implements PreferenceType<CommandSelectorType.CommandData, Integer, CommandSelectorType.IntPredicate> {

	@Override
	public MapCodec<CommandData> getDefinitionCodec() {
		return CommandData.CODEC;
	}

	@Override
	public Codec<Integer> getValueCodec() {
		return Codec.INT;
	}

	@Override
	public Codec<IntPredicate> getValuePredicateCodec() {
		return IntPredicate.CODEC;
	}


	@Override
	public void onClicked(
			ServerPlayer player, Holder<Preference<CommandData, Integer, ?>> definition, PreferenceMenu menu
	) {
		var data = PreferenceData.getForPlayer(player);
		var def = definition.value().definition();

		new SubMenu(
				menu, player,
				submenu -> {
					List<GuiElement> buttons = new ArrayList<>();
					var value = data.get(definition);
					for (int i = 0; i < def.commands.size(); i++) {
						var command = def.commands.get(i);
						if (i == value) {
							buttons.add(
									Icon.createBuilder(command.selectedIcon.orElse(command.icon), player).build()
							);
						} else {
							int v = i;
							buttons.add(
									Icon.createBuilder(command.icon, player).setCallback(
											() -> {
												runCommand(player, command);
												data.set(definition, v);
												submenu.refreshButtons();
											}
									).build()
							);
						}

					}
					return buttons;
				},
				def.commands.size(), def.menuTitle()
		).open();
	}

	private void runCommand(ServerPlayer player, CommandData.CommandEntry command) {
		player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withSuppressedOutput().withPermission(LevelBasedPermissionSet.GAMEMASTER),
				command.command
		);
	}

	@Override
	public void initDefaultValue(
			ServerPlayer player,
			Holder<Preference<CommandSelectorType.CommandData, Integer, ?>> definition
	) {
		runCommand(player, definition.value().definition().commands.get(definition.value().defaultValue()));
	}

	public record CommandData(List<CommandEntry> commands, Component menuTitle) {

		public static final MapCodec<CommandData> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						CommandEntry.CODEC.listOf().fieldOf("commands").forGetter(CommandData::commands),
						ComponentSerialization.CODEC.fieldOf("menu_title").forGetter(CommandData::menuTitle)
				).apply(instance, CommandData::new)
		);

		public record CommandEntry(String command, DisplayItemData icon, Optional<DisplayItemData> selectedIcon) {
			public static final Codec<CommandEntry> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							Codec.STRING.fieldOf("command").forGetter(CommandEntry::command),
							DisplayItemData.CODEC.fieldOf("icon").forGetter(CommandEntry::icon),
							DisplayItemData.CODEC.optionalFieldOf("selected_icon").forGetter(CommandEntry::selectedIcon)
					).apply(instance, CommandEntry::new)
			);
		}

	}

	public record IntPredicate(
			MinMaxBounds.Ints num
	) implements Predicate<Integer> {

		public static final Codec<IntPredicate> CODEC = MinMaxBounds.Ints.CODEC.xmap(
				IntPredicate::new, IntPredicate::num
		);

		@Override
		public boolean test(Integer i) {
			return num.matches(i);
		}
	}
}
