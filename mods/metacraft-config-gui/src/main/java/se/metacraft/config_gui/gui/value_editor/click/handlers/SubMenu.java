package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.TreePMap;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.parser.metadata.Entries;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.*;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;
import se.metacraft.config_gui.util.EditorGUIPreferences;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public record SubMenu(SubMenuKey key) implements ClickHandler {

	public SubMenu(String string) {
		this(new S(string));
	}

	public SubMenu(int num) {
		this(new I(num));
	}

	public static final MapCodec<SubMenu> CODEC = SubMenuKey.LENINENT_REGISTRY_CODEC.fieldOf("submenu$key").xmap(
		SubMenu::new, SubMenu::key
	);

	private static <T> ClickHandlerGUI openInternal(
		Object object, Object nonNullObject, CodecDialog.Type type,
		HolderLookup.Provider provider, ClickHandlerGUI parentGUI, SubMenuKey key
	) {
		if (type.element().hasContainerType(ContainerType.EITHER)) {
			return AlternativesEditor.create(
				nonNullObject, type, provider,
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			);
		}
		if (type.element().hasContainerType(ContainerType.DISPATCHED_EITHER)) {
			var data = type.element().getContainerData(ContainerType.DISPATCHED_EITHER).orElseThrow();
			var container = type.element().getContainer(ContainerType.DISPATCHED_EITHER);
			var keyComponent = container.orElseThrow().components().getFirst();

			var keyValue = data.keyByValue().apply(nonNullObject).resultOrPartial(
				ConfigGUI.LOGGER::error
			);

			if (keyValue.isEmpty()) return parentGUI;
			return new DispatchedEitherEditor<>(
				CodecDialog.Type.from(keyComponent), keyValue.get(),
				o -> data.decoderByKey().apply(o).flatMap(codec -> {
					if (codec instanceof MapCodec<?> mapCodec) {
						return DataResult.success(CodecDialog.Type.from(CodecParser.parse(mapCodec, provider)));
					} else {
						return DataResult.error(() -> "Does not know how to parse " + codec);
					}
				}),
				nonNullObject,
				type.element().codec(),
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			);
		}
		if (type.element().metadata(MetadataKey.ENTRIES).isPresent()) {
			return new DropdownEditor<>(
				(Codec<T>) type.element().codec(), (T) nonNullObject,
				PCollectionsHelper.collectToMap(
					type.element().metadata(MetadataKey.ENTRIES).get().strings().stream(),
					Entries.Entry::key,
					e -> (T) e.value(),
					TreePMap.empty()
				),
				type.element().metadata(MetadataKey.ENTRIES).get().strings().size() > CodecDialog.DROPDOWN_USE_SEARCH_BAR_THRESHOLD ?
					Optional.of("") : Optional.empty(),
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			);
		}
		if (type.element().hasContainerType(ContainerType.RECORD)) {
			return MapCodecEditor.create(
				(T) nonNullObject, (Codec<T>) type.element().codec(),
				provider, Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			);
		}
		if (type.element().hasContainerType(ContainerType.LIST)) {
			var range = type.element().nestedMetadata(MetadataKey.RANGE);
			return ListEditor.create(
				(T) nonNullObject,
				(Codec<T>) type.element().codec(),
				range.map(value -> OptionalInt.of(value.min().intValue())).orElseGet(OptionalInt::empty),
				range.map(value -> OptionalInt.of(value.max().intValue())).orElseGet(OptionalInt::empty),
				provider,
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			).orElse(parentGUI);
		}
		if (type.element().hasContainerType(ContainerType.MAP) || type.element().hasContainerType(ContainerType.DISPATCHED_MAP)) {
			return MapEditor.create(
				(T) nonNullObject,
				(Codec<T>) type.element().codec(),
				provider,
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
			).orElse(parentGUI);
		}
		if (type.optional()) {
			var unnamed = type.element().getUnderlying(e -> e.nestedMetadata(MetadataKey.NAMED_FIELD).isEmpty());
			if (unnamed.isPresent()) {
				return OptionalValueEditor.create(
					nonNullObject, type,
					object != null,
					Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key))
				);
			}
		}
		var recursion = type.element().getContainerData(ContainerType.RECURSIVE);
		if (recursion.isPresent()) {
			return open(
				object, CodecDialog.Type.from(recursion.get().wrapped().get()), provider, parentGUI, key
			);
		}
		if (type.element().hasContainerType(ContainerType.UNIT)) {
			return new PopupScreen(
				Component.literal("Unit"),
				List.of(new PlainMessage(Component.literal("This is a unit codec, it does not have any settings"), 300)),
				new ValueEditor.SubMenuParentInfo(parentGUI, key)
			);
		}
		if (type.element().getUnderlying(c -> c.codec() == Codec.PASSTHROUGH).isPresent()) {
			return new RawInputScreen.Editor<>(
				(Codec<T>) type.element().codec(),
				(Optional<T>) Optional.ofNullable(object),
				Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key)),
				Optional.empty()
			);
		}
		return new SingleValueEditor<>(
			nonNullObject, type, type.element().codec(),
			Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key)), Optional.empty()
		);
	}

	public static ClickHandlerGUI open(
		Object object, CodecDialog.Type type,
		HolderLookup.Provider provider, ClickHandlerGUI parentGUI, SubMenuKey key
	) {
		if (type.optional() && parentGUI instanceof OptionalValueEditor<?>) {
			type = type.asRequired();
		}
		if (object != null) {
			return openInternal(object, object, type, provider, parentGUI, key);
		} else {
			var t = type;
			return type.createEmpty(provider).mapOrElse(
				value -> openInternal(null, value, t, provider, parentGUI, key),
				err -> new RawInputScreen.ScreenGenerator(
					t.element().codec(),
					value -> openInternal(null, value, t, provider, parentGUI, key),
					Optional.of(new ValueEditor.SubMenuParentInfo(parentGUI, key)),
					Optional.of(err.message())
				)
			);
		}
	}

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (key == EditorGUIPreferences.PreferencePointer.INSTANCE) {
			return MapEditor.create(
				EditorGUIPreferences.get(player), EditorGUIPreferences.CODEC, player.registryAccess(),
				Optional.of((childGUI, p) -> {
					if (childGUI instanceof ValueEditor<?> editor) {
						return PopupScreen.dataResult(
							editor.getValue(p.registryAccess()),
							v -> {
								if (v.isPresent() && v.get() instanceof EditorGUIPreferences prefs) {
									EditorGUIPreferences.update(p, prev -> prefs);
								}
								return gui;
							},
							gui
						);
					}
					return gui;
				})
			).orElse(gui);
		}
		if (gui instanceof WithSubMenus c) {
			var object = c.getObject(key, player);
			var type = c.getType(key, player);
			if (type == null) return gui;
			return open(object, type, player.registryAccess(), gui, key);
		}
		return gui;
	}

	@Override
	public ClickHandlerType<SubMenu> type() {
		return ClickHandlerRegistry.SUBMENU;
	}

	public interface SubMenuKey {
		Codec<SubMenuKey> LENINENT_REGISTRY_CODEC = Codec.either(
			Codec.lazyInitialized(() -> REGISTRY_CODEC),
			Codec.either(
				Codec.INT,
				Codec.STRING
			)
		).xmap(
			either -> either.map(l -> l, r -> r.map(I::new, S::new)),
			key -> switch (key) {
				case I i -> Either.right(Either.left(i.i));
				case S s -> Either.right(Either.right(s.s));
				default -> Either.left(key);
			}
		);

		MapCodec<? extends SubMenuKey> codec();
	}

	public static final Registry<MapCodec<? extends SubMenuKey>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends SubMenuKey>>create(
		ResourceKey.createRegistryKey(ConfigGUI.getID("submenu_key"))
	).buildAndRegister();

	private static final Codec<SubMenuKey> REGISTRY_CODEC = REGISTRY.byNameCodec().dispatch(
		SubMenuKey::codec, c -> c
	);

	public record S(String s) implements SubMenuKey {

		public static final MapCodec<S> CODEC = Codec.STRING.fieldOf("s").xmap(S::new, S::s);

		@Override
		public MapCodec<? extends SubMenuKey> codec() {
			return CODEC;
		}
	}

	public record I(int i) implements SubMenuKey {

		public static final MapCodec<I> CODEC = Codec.INT.fieldOf("i").xmap(I::new, I::i);

		@Override
		public MapCodec<? extends SubMenuKey> codec() {
			return CODEC;
		}
	}

	public static void init() {
		register("s", S.CODEC);
		register("i", I.CODEC);
		register("map_target", MapEditor.MapTarget.CODEC);
		register("new_map_entry", MapEditor.New.CODEC);
		register("optional", OptionalValueEditor.OptionalKey.CODEC);
		register("preferences", EditorGUIPreferences.PreferencePointer.CODEC);
	}

	private static void register(String id, MapCodec<? extends SubMenuKey> codec) {
		Registry.register(REGISTRY, id, codec);
	}
}
