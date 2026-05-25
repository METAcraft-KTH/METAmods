package nu.metacraft.core.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilderCreator;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.util.DisplayItemData;

import java.util.Optional;
import java.util.function.Predicate;

public interface PreferenceType<T, V, P extends Predicate<V>> {

	Registry<PreferenceType<?, ?, ?>> REGISTRY = FabricRegistryBuilder.<PreferenceType<?, ?, ?>>create(
			ResourceKey.createRegistryKey(METAcraftCore.getID("preference_type"))
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
						DisplayItemData.CODEC.fieldOf("items").forGetter(Icon::items),
						getValuePredicateCodec().optionalFieldOf("predicate").forGetter(Icon::predicate)
				).apply(instance, Icon::new)
		);
	}

	default boolean isVisibleTo(ServerPlayer player, Holder<Preference<T, V, ?>> definition) {
		return true;
	}

	void onClicked(ServerPlayer player, Holder<Preference<T, V, ?>> definition, PreferenceMenu menu);

	default void initDefaultValue(ServerPlayer player, Holder<Preference<T, V, ?>> definition) {}

	record Icon<V, P extends Predicate<V>>(
			DisplayItemData items, Optional<P> predicate
	) {

		public boolean shouldShowIcon(V value) {
			return predicate.map(p -> p.test(value)).orElse(true);
		}

		private static <T> void set(AnimatedGuiElementBuilder builder, DataComponentType<T> c, ItemStack i) {
			builder.setComponent(c, i.get(c));
		}

		public static GuiElementBuilderCreator<?> createBuilder(DisplayItemData items, ServerPlayer player) {
			var icon = items.getItems(player);
			if (icon.size() == 1) {
				return GuiElementBuilder.from(icon.getFirst());
			} else {
				var animatedBuilder = new AnimatedGuiElementBuilder().setInterval(5).setRandom(true);
				for (var i : icon) {
					animatedBuilder.setItem(i.getItem());
					for (var c : i.getComponentsPatch().entrySet()) {
						set(animatedBuilder, c.getKey(), i);
					}
					animatedBuilder.saveItemStack();
				}
				return animatedBuilder;
			}
		}
	}

}
