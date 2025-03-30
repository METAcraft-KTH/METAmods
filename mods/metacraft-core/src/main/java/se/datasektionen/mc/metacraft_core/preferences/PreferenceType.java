package se.datasektionen.mc.metacraft_core.preferences;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilderInterface;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface PreferenceType<T, V, P extends Predicate<V>> {

	Registry<PreferenceType<?, ?, ?>> REGISTRY = FabricRegistryBuilder.<PreferenceType<?, ?, ?>>createSimple(
			RegistryKey.ofRegistry(METAcraftCore.getID("preference_type"))
	).buildAndRegister();

	//PreferenceType<?> LANGUAGE = register("language", null);
	CommandSelectorType COMMAND_SELECTOR = register("command_selector", new CommandSelectorType());
	CommandToggleType COMMAND_TOGGLE = register("command_toggle", new CommandToggleType());

	private static <T extends PreferenceType<?, ?, ?>> T register(String id, T type) {
		return Registry.register(REGISTRY, id, type);
	}

	MapCodec<T> getDefinitionCodec();
	Codec<V> getValueCodec();
	Codec<P> getValuePredicateCodec();

	default Codec<Icon<V, P>> getIconCodec() {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						IconData.CODEC.fieldOf("items").forGetter(Icon::items),
						getValuePredicateCodec().optionalFieldOf("predicate").forGetter(Icon::predicate)
				).apply(instance, Icon::new)
		);
	}

	default boolean isVisibleTo(ServerPlayerEntity player, RegistryEntry<Preference<T, V, ?>> definition) {
		return true;
	}

	void onClicked(ServerPlayerEntity player, RegistryEntry<Preference<T, V, ?>> definition, PreferenceMenu menu);

	default void initDefaultValue(ServerPlayerEntity player, RegistryEntry<Preference<T, V, ?>> definition) {}

	record IconData(Either<List<ItemStack>, LootTable> items) {
		public static final IconData EMPTY = new IconData(Either.left(List.of()));

		private static final Codec<Either<List<ItemStack>, LootTable>> ICON_CODEC = Codec.either(
				Codec.withAlternative(ItemStack.CODEC.listOf(), ItemStack.CODEC, List::of), LootTable.CODEC
		);

		public static final Codec<IconData> CODEC = ICON_CODEC.xmap(IconData::new, IconData::items);

		private static <T> void set(AnimatedGuiElementBuilder builder, ComponentType<T> c, ItemStack i) {
			builder.setComponent(c, i.get(c));
		}

		public GuiElementBuilderInterface<?> createBuilder(ServerPlayerEntity player) {
			Supplier<LootWorldContext> ctx = () -> new LootWorldContext.Builder(player.getServerWorld())
					.add(LootContextParameters.ORIGIN, player.getPos())
					.luck(player.getLuck())
					.add(LootContextParameters.THIS_ENTITY, player)
					.build(LootContextTypes.CHEST);
			var icon = items().map(
					items -> items,
					lootTable -> lootTable.generateLoot(ctx.get())
			);
			if (icon.isEmpty()) {
				icon = List.of(new ItemStack(Items.BARRIER));
			}
			if (icon.size() == 1) {
				return GuiElementBuilder.from(icon.getFirst());
			} else {
				var animatedBuilder = new AnimatedGuiElementBuilder().setInterval(5).setRandom(true);
				for (var i : icon) {
					animatedBuilder.setItem(i.getItem());
					for (var c : i.getComponentChanges().entrySet()) {
						set(animatedBuilder, c.getKey(), i);
					}
					animatedBuilder.saveItemStack();
				}
				return animatedBuilder;
			}
		}
	}

	record Icon<V, P extends Predicate<V>>(
			IconData items, Optional<P> predicate
	) {

		public boolean shouldShowIcon(V value) {
			return predicate.map(p -> p.test(value)).orElse(true);
		}

	}

}
