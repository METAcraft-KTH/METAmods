package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.util.helper.DataResultHelper;
import se.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import se.metacraft.config.parser.metadata.Comments;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;
import se.metacraft.config_gui.util.EditorGUIPreferences;

import java.util.*;

public record MapCodecEditor<T>(
	PMap<String, Object> object,
	PMap<String, CodecDialog.Type> types,
	PMap<String, Component> tooltips,
	Codec<T> codec, Optional<ParentInfo> parent
) implements ValueEditor<T>, WithSubMenus {

	public static <T> MapCodecEditor<T> create(
		T value, Codec<T> codec, HolderLookup.Provider lookup, Optional<ParentInfo> parent
	) {
		var types = CodecDialog.getMapCodecTypes(codec, lookup);
		return new MapCodecEditor<>(
			CodecDialog.getMapCodecElements(codec, value, lookup, types.getFirst()),
			types.getFirst(), types.getSecond().map(Comments::comments).map(
				comments -> PCollectionsHelper.collectToMap(
					comments.entrySet().stream().filter(k -> k.getKey() instanceof String),
					e -> (String) e.getKey(),
					Map.Entry::getValue
				)
			).orElse(HashTreePMap.empty()),
			codec, parent
		);
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		return CodecDialog.createDialog(object, types, tooltips, player.registryAccess(), EditorGUIPreferences.get(player));
	}

	public MapCodecEditor<T> withObject(PMap<String, Object> object) {
		if (object == this.object) return this;
		return new MapCodecEditor<>(object, types, tooltips, codec, parent);
	}

	@Override
	public MessageUpdate updateFromMessage(ServerPlayer player, CompoundTag tag) {
		MapCodecEditor<T> toReturn = this;
		List<String> errors = new ArrayList<>();
		for (var key : types.keySet()) {
			if (tag.contains(key)) {
				var type = types.get(key);
				var result = type.parse(player.registryAccess(), tag.get(key)).ifError(
					err -> errors.add(err.message())
				).resultOrPartial();
				if (result.isPresent()) {
					toReturn = toReturn.withObject(toReturn.object.plus(key, result.get()));
				}
			}
		}
		if (!errors.isEmpty()) {
			return new MessageUpdate(
				new PopupScreen(
					PopupScreen.FAILED,
					errors.stream().<DialogBody>map(
						err -> new PlainMessage(Component.literal(err).withColor(TextColor.RED), PlainMessage.DEFAULT_WIDTH)
					).toList(),
					new SimpleSubMenuParentInfo(toReturn)
				),
				false
			);
		}
		return MessageUpdate.simple(toReturn);
	}

	@Override
	public ClickHandlerGUI updateFromChild(
		HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position
	) {
		if (position instanceof SubMenu.S(String s) && child instanceof ValueEditor<?> editor) {
			return PopupScreen.dataResult(
				editor.getValue(lookup).map(
					opt -> opt.map(
						v -> withObject(object.plus(s, v))
					).orElse(
						withObject(object.minus(s))
					)
				),
				this
			);
		}
		return this;
	}

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		DataResult<PMap<String, Object>> map = DataResult.success(HashTreePMap.empty());
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		for (var entry : object.entrySet()) {
			var key = entry.getKey();
			var value = entry.getValue();
			var type = types.get(key);
			if (CodecDialog.isMapInlined(type)) {
				//noinspection unchecked
				map = map.flatMap(
					m -> DataResultHelper.withPartialIfError(
						((Codec<Object>) type.fieldElement().codec()).encodeStart(
							ctx, value
						).map(result -> {
							var modified = m;
							if (result instanceof Map<?,?> encodedMap) {
								for (var e : encodedMap.entrySet()) {
									if (e.getKey() instanceof String s) {
										modified = modified.plus(s, e.getValue());
									}
								}
							}
							return modified;
						}),
						m
					)
				);
			} else {
				//noinspection unchecked
				map = map.flatMap(
					m -> DataResultHelper.withPartialIfError(
						((Codec<Object>) type.element().codec()).encodeStart(
							ctx, value
						).map(result -> m.plus(key, result)),
						m
					)
				);
			}
		}
		return map.flatMap(m -> codec.parse(ctx, m).map(Optional::of));
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (!(key instanceof SubMenu.S(String s))) return null;
		return types.get(s);
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (!(key instanceof SubMenu.S(String s))) return null;
		return object.get(s);
	}
}
