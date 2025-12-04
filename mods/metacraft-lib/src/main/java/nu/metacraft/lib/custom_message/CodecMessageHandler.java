package nu.metacraft.lib.custom_message;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;
import java.util.function.Function;

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

	public interface Handler<T> {
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

	public static <T> ClickEvent createSimpleClickEvent(
			Holder.Reference<CodecMessageHandler<T>> handler, T value,
			Function<DynamicOps<Tag>, RegistryOps<Tag>> registryOpsWrapper
	) {
		return new ClickEvent.Custom(
				handler.key().location(),
				handler.value().encode(value, registryOpsWrapper)
		);
	}
}
