package se.metacraft.config_gui.gui.value_editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.*;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Back;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Resubmit;
import se.metacraft.config_gui.mixin.TagParserAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RawInputScreen extends ClickHandlerGUI {

	String DATA = "data";
	String TYPE = "type";

	@Override
	default MessageUpdate updateFromMessage(ServerPlayer player, CompoundTag tag) {
		return MessageUpdate.simple(
			tag.read(TYPE, REGISTRY.byNameCodec()).map(type -> {
				var value = tag.getString(DATA);
				if (value.isEmpty()) return null;
				var result = type.parse(decoder(), value.get(), player.registryAccess());
				return onInputReceived(player, result, type);
			}).orElse(this)
		);
	}

	Decoder<?> decoder();
	List<DialogBody> body();
	ClickHandlerGUI onInputReceived(ServerPlayer player, DataResult<?> object, EncoderType<?> encoderType);
	default String initial(HolderLookup.Provider lookup) {
		return "";
	}
	default boolean isSelectedEncoder(Holder.Reference<EncoderType<?>> encoderType) {
		return REGISTRY.getDefaultKey().equals(encoderType.key().identifier());
	}

	default ActionButton exitButton(HolderLookup.Provider lookup) {
		return new ActionButton(
			new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH),
			Optional.of(ClickHandler.click(lookup, Back.INSTANCE, false))
		);
	}

	default List<ActionButton> buttons(HolderLookup.Provider lookup) {
		return List.of(new ActionButton(
			new CommonButtonData(Component.translatable("gui.friends.confirm_title"), CommonButtonData.DEFAULT_WIDTH),
			Optional.of(ClickHandler.click(lookup, Resubmit.INSTANCE, true))
		));
	}

	@Override
	default Holder<Dialog> createDialog(ServerPlayer player) {
		return CodecDialog.template(
			body(),
			List.of(
				new Input(DATA, new TextInput(
					200, Component.literal("Value"), true,
					initial(player.registryAccess()), Integer.MAX_VALUE,
					Optional.of(new TextInput.MultilineOptions(
						Optional.empty(), Optional.of(150)
					))
				)),
				new Input(TYPE, new SingleOptionInput(
					200,
					REGISTRY.listElements().map(
						element -> new SingleOptionInput.Entry(
							element.key().identifier().toString(),
							Optional.of(element.value().display()),
							isSelectedEncoder(element)
						)
					).toList(),
					Component.literal("Type"),
					true
				))
			),
			buttons(player.registryAccess()),
			player.registryAccess(), 1,
			exitButton(player.registryAccess())
		);
	}

	record EncoderType<T>(Component display, Function<String, DataResult<T>> parser, DynamicOps<T> converter, Function<T, String> writer) {
		public <O> DataResult<O> parse(Decoder<O> codec, String value, HolderLookup.Provider lookup) {
			return parser.apply(value).flatMap(
				v -> codec.parse(lookup.createSerializationContext(converter), v)
			);
		}
	}

	DefaultedMappedRegistry<EncoderType<?>> REGISTRY = FabricRegistryBuilder.<EncoderType<?>>createDefaulted(
		ResourceKey.createRegistryKey(ConfigGUI.getID("default_value_encoder_type")), ConfigGUI.getID("nbt")
	).buildAndRegister();

	static void init() {
		Registry.register(
			REGISTRY,
			REGISTRY.getDefaultKey(),
			new EncoderType<>(
				Component.literal("NBT"),
				string -> {
					try {
						return DataResult.success(TagParserAccessor.getNBTOpsParser().parseFully(string));
					} catch (CommandSyntaxException err) {
						return DataResult.error(err::getMessage);
					}
				},
				NbtOps.INSTANCE,
				value -> NbtUtils.toPrettyComponent(value).getString()
			)
		);
		register("json", new EncoderType<>(
			Component.literal("JSON"),
			string -> {
				try {
					return DataResult.success(JsonParser.parseString(string));
				} catch (JsonSyntaxException err) {
					return DataResult.error(err::getMessage);
				}
			},
			JsonOps.INSTANCE,
			JsonElement::toString
		));
	}

	private static void register(String id, EncoderType<?> type) {
		Registry.register(REGISTRY, ConfigGUI.getID(id), type);
	}

	record Editor<T>(Codec<T> codec, Optional<T> value, Optional<ParentInfo> parent, EncoderType<?> type, Optional<String> error) implements RawInputScreen, ValueEditor<T> {

		public Editor(Codec<T> codec, Optional<T> value, Optional<ParentInfo> parent, Optional<String> error) {
			this(codec, value, parent, REGISTRY.getAny().orElseThrow().value(), error);
		}

		@Override
		public Decoder<?> decoder() {
			return codec;
		}

		@Override
		public List<DialogBody> body() {
			List<DialogBody> body = new ArrayList<>();
			error.ifPresent(error -> body.add(new PlainMessage(Component.literal(error).withColor(TextColor.RED), 200)));
			return body;
		}

		@Override
		public ActionButton exitButton(HolderLookup.Provider lookup) {
			return new ActionButton(
				new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH),
				Optional.of(ClickHandler.click(lookup, Back.INSTANCE, true))
			);
		}

		private static <T, C> String toString(
			@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<T> value, Codec<T> codec,
			EncoderType<C> type, HolderLookup.Provider lookup
		) {
			return value.flatMap(
				v -> codec.encodeStart(
					lookup.createSerializationContext(type.converter), v
				).resultOrPartial()
			).map(type.writer).orElse("");
		}

		@Override
		public String initial(HolderLookup.Provider lookup) {
			return toString(value, codec, type, lookup);
		}
		@Override
		public boolean isSelectedEncoder(Holder.Reference<EncoderType<?>> encoderType) {
			return encoderType.value() == type;
		}

		@Override
		public ClickHandlerGUI onInputReceived(ServerPlayer player, DataResult<?> object, EncoderType<?> type) {
			if (object.hasResultOrPartial()) {
				//noinspection unchecked
				return new Editor<>(codec, (Optional<T>) object.resultOrPartial(), parent, type, object.error().map(DataResult.Error::message));
			}
			return new Editor<>(codec, value, parent, type, object.error().map(DataResult.Error::message));
		}

		@Override
		public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
			return DataResult.success(value);
		}
	}

	record ScreenGenerator(
		Decoder<?> decoder, Function<Object, ClickHandlerGUI> onSuccess, Optional<ParentInfo> parent, Optional<String> error
	) implements RawInputScreen {

		@Override
		public List<DialogBody> body() {
			List<DialogBody> body = new ArrayList<>();
			error.ifPresent(error -> body.add(new PlainMessage(Component.literal(error).withColor(TextColor.RED), 200)));
			return body;
		}

		@Override
		public ClickHandlerGUI onInputReceived(ServerPlayer player, DataResult<?> object, EncoderType<?> type) {
			if (object.result().isPresent()) {
				return onSuccess.apply(object.result().get());
			} else {
				return new ScreenGenerator(decoder, onSuccess, parent, object.error().map(DataResult.Error::message));
			}
		}
	}
}
