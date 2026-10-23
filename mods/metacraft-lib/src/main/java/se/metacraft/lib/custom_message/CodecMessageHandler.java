package se.metacraft.lib.custom_message;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;
import java.util.function.Function;

/**
 * A simple message handler which will first attempt to decode the message using the given codec and then passes the
 * resulting object into the message.
 * If the codec is unable to parse the given data, the server will log the error and discard the message.
 * Any data not parsed by the codec will be discarded.
 * Also provides the utility function
 * {@link CodecMessageHandler#createSimpleClickEvent(Holder.Reference, Object, Function)}
 * to make it easy to design dynamic dialogs which will target this handler.
 *
 * @param onMessage The code to execute.
 * @param codec The codec to parse.
 * @param <T> The type of the object produced by the codec.
 */
public record CodecMessageHandler<T>(
		Handler<T> onMessage, Codec<T> codec
) implements CustomMessageHandler {

	@Override
	public void handleMessage(Optional<Tag> payload, MinecraftServer server, PotentialPlayer player) {
		payload.flatMap(
				data -> codec.parse(
						server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
						data
				).resultOrPartial(METAcraftLib.LOGGER::error)
		).ifPresent(value -> onMessage.handle(value, server, player));
	}

	@FunctionalInterface
	public interface Handler<T> {
		/**
		 * Handler for a message.
		 * @param data The payload of the message.
		 * @param server The server accepting this message.
		 * @param player The player who sent the request. Note that {@link PotentialPlayer#getPlayer(MinecraftServer) might be empty in the configuration stage, but {@link PotentialPlayer#getPlayerID()}} might not be.
		 */
		void handle(T data, MinecraftServer server, PotentialPlayer player);
	}

	public Optional<Tag> encode(
			T value, Function<DynamicOps<Tag>, RegistryOps<Tag>> registryOpsWrapper
	) {
		return codec.encodeStart(
				registryOpsWrapper.apply(NbtOps.INSTANCE),
				value
		).resultOrPartial(METAcraftLib.LOGGER::error);
	}

	/**
	 * Creates a click event which will be accepted by the given handler.
	 * Note that this will NOT send inputs.
	 * @param handler The holder containing the handler (use {@link net.minecraft.core.Registry#registerForHolder(Registry, ResourceKey, Object)} when registering your handler to get an easily accessible {@link Holder}).
	 * @param value The value to encode using the codec.
	 * @param registryOpsWrapper Function to convert {@link NbtOps#INSTANCE} into {@link RegistryOps}. Will probably be something like `lookup::createSerializationContext`.
	 * @return A click event you can put into text components or dialog buttons.
	 * @param <T> The type of the object.
	 */
	public static <T> ClickEvent createSimpleClickEvent(
			Holder.Reference<CodecMessageHandler<T>> handler, T value,
			Function<DynamicOps<Tag>, RegistryOps<Tag>> registryOpsWrapper
	) {
		return new ClickEvent.Custom(
				handler.key().identifier(),
				handler.value().encode(value, registryOpsWrapper)
		);
	}

	/**
	 * Creates a click event which will be accepted by the given handler.
	 * Note that this will NOT send inputs.
	 * @param handler The holder containing the handler (use {@link net.minecraft.core.Registry#registerForHolder(Registry, ResourceKey, Object)} when registering your handler to get an easily accessible {@link Holder}).
	 * @param value The value to encode using the codec.
	 * @param registryOpsWrapper Function to convert {@link NbtOps#INSTANCE} into {@link RegistryOps}. Will probably be something like `lookup::createSerializationContext`.
	 * @return A click action you can put into dialog buttons.
	 * @param <T> The type of the object.
	 */
	public static <T> Action createSimpleAction(
		Holder.Reference<CodecMessageHandler<T>> handler, T value,
		Function<DynamicOps<Tag>, RegistryOps<Tag>> registryOpsWrapper
	) {
		return new StaticAction(
			createSimpleClickEvent(handler, value, registryOpsWrapper)
		);
	}

	/**
	 * Creates a click event which will be accepted by the given handler.
	 * This WILL send inputs.
	 * @param handler The holder containing the handler (use {@link net.minecraft.core.Registry#registerForHolder(Registry, ResourceKey, Object)} when registering your handler to get an easily accessible {@link Holder}).
	 * @param value The value to encode using the codec.
	 * @param registryOpsWrapper Function to convert {@link NbtOps#INSTANCE} into {@link RegistryOps}. Will probably be something like `lookup::createSerializationContext`.
	 * @return A click action you can put into dialog buttons.
	 * @param <T> The type of the object.
	 * @throws IllegalArgumentException If handler does not encode value as a map.
	 */
	public static <T> Action createDynamicAction(
		Holder.Reference<CodecMessageHandler<T>> handler, T value,
		Function<DynamicOps<Tag>, RegistryOps<Tag>> registryOpsWrapper
	) {
		return new CustomAll(
			handler.key().identifier(),
			handler.value().encode(value, registryOpsWrapper).map(e -> {
				if (e instanceof CompoundTag tag) {
					return tag;
				} else {
					throw new IllegalArgumentException("handler does not encode value as a map");
				}
			})
		);
	}
}
