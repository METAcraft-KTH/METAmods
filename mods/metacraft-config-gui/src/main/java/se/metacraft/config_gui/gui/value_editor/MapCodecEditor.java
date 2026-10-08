package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import se.metacraft.config.parser.metadata.Comments;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
		return CodecDialog.createDialog(object, types, tooltips, player.registryAccess());
	}

	public MapCodecEditor<T> withObject(PMap<String, Object> object) {
		if (object == this.object) return this;
		return new MapCodecEditor<>(object, types, tooltips, codec, parent);
	}

	@Override
	public ValueEditor<T> updateFromMessage(ServerPlayer player, CompoundTag tag) {
		MapCodecEditor<T> toReturn = this;
		for (var key : types.keySet()) {
			if (tag.contains(key)) {
				var type = types.get(key);
				var result = type.parse(player.registryAccess(), tag.get(key)).resultOrPartial(
					err -> player.sendSystemMessage(Component.literal(err))
				);
				if (result.isPresent()) {
					toReturn = toReturn.withObject(toReturn.object.plus(key, result.get()));
				}
			}
		}
		return toReturn;
	}

	@Override
	public ValueEditor<T> updateFromChild(
		HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position
	) {
		if (position instanceof SubMenu.S(String s) && child instanceof ValueEditor<?> editor) {
			return editor.getValue(lookup).map(
				v -> withObject(object.plus(s, v))
			).orElse(
				withObject(object.minus(s))
			);
		}
		return this;
	}

	@Override
	public Optional<T> getValue(HolderLookup.Provider lookup) {
		Map<String, Object> map = new HashMap<>();
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		object.forEach((key, value) -> {
			var type = types.get(key);
			if (CodecDialog.isMapInlined(type)) {
				//noinspection unchecked
				((Codec<Object>) type.fieldElement().codec()).encodeStart(
					ctx, value
				).resultOrPartial(ConfigGUI.LOGGER::error).ifPresent(result -> {
					if (result instanceof Map<?,?> m) {
						m.forEach((k, v) -> {
							map.put((String) k, v);
						});
					}
				});
			} else {
				//noinspection unchecked
				((Codec<Object>) type.element().codec()).encodeStart(
					ctx, value
				).resultOrPartial(ConfigGUI.LOGGER::error).ifPresent(result -> {
					map.put(key, result);
				});
			}
		});
		return codec.parse(ctx, map).resultOrPartial();
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
