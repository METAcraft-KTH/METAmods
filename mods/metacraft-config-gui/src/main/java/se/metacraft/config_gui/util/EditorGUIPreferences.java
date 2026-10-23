package se.metacraft.config_gui.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.util.METACodecs;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.ServerPlayerExtension;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;

import java.util.function.UnaryOperator;

public record EditorGUIPreferences(PMap<PreferenceType<?>, Object> map) {

	public static final EditorGUIPreferences EMPTY = new EditorGUIPreferences(HashTreePMap.empty());

	public record PreferenceType<T>(Codec<T> codec, T defaultValue) {}

	public static final Registry<PreferenceType<?>> PREFERENCE_REGISTRY = FabricRegistryBuilder.<PreferenceType<?>>create(
		ResourceKey.createRegistryKey(ConfigGUI.getID("config_preference_type"))
	).buildAndRegister();

	public static final Codec<EditorGUIPreferences> CODEC = METACodecs.wrapPMapCodec(
		Codec.<PreferenceType<?>, Object>dispatchedMap(
			PREFERENCE_REGISTRY.byNameCodec(), PreferenceType::codec
		), HashTreePMap.empty()
	).xmap(EditorGUIPreferences::new, EditorGUIPreferences::map);

	public static final PreferenceType<Boolean> USE_NUMBER_SLIDERS = register("use_number_sliders", Codec.BOOL, true);

	public <T> T get(PreferenceType<T> type) {
		//noinspection unchecked
		return (T) map.getOrDefault(type, type.defaultValue());
	}

	public <T> EditorGUIPreferences set(PreferenceType<T> type, T value) {
		return new EditorGUIPreferences(map.plus(type, value));
	}

	public EditorGUIPreferences remove(PreferenceType<?> type) {
		return new EditorGUIPreferences(map.minus(type));
	}

	public static EditorGUIPreferences get(ServerPlayer player) {
		return ((ServerPlayerExtension) player).metacraft$getEditorPreferences();
	}

	public static void update(ServerPlayer player, UnaryOperator<EditorGUIPreferences> modifier) {
		((ServerPlayerExtension) player).metacraft$updateEditorPreferences(modifier);
	}


	public static void init() {

	}

	private static <T> PreferenceType<T> register(String id, Codec<T> codec, T defaultValue) {
		return Registry.register(PREFERENCE_REGISTRY, ConfigGUI.getID(id), new PreferenceType<>(codec, defaultValue));
	}

	public static class PreferencePointer implements SubMenu.SubMenuKey {

		public static final PreferencePointer INSTANCE = new PreferencePointer();
		public static final MapCodec<PreferencePointer> CODEC = MapCodec.unit(INSTANCE);

		private PreferencePointer() {}

		@Override
		public MapCodec<? extends SubMenu.SubMenuKey> codec() {
			return CODEC;
		}
	}
}
