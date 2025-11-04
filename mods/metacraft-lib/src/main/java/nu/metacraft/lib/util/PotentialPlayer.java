package nu.metacraft.lib.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import java.util.UUID;

public sealed abstract class PotentialPlayer permits PotentialPlayer.Absent, PotentialPlayer.Present {

	public static final Codec<PotentialPlayer> CODEC = Codec.lazyInitialized(() ->
		Codec.either(Present.CODEC, Absent.CODEC).xmap(
				Either::unwrap, e -> switch (e) {
					case Absent a -> Either.right(a);
					case Present a -> Either.left(a);
				}
		)
	);

	public static MapCodec<PotentialPlayer> getMapCodec(String name) {
		return CODEC.optionalFieldOf(name).xmap(
				value -> value.orElse(Absent.INSTANCE),
				value -> value instanceof Absent ? Optional.empty() : Optional.of(value)
		);
	}

	public abstract Optional<ServerPlayer> getPlayer(MinecraftServer server);

	public static PotentialPlayer get(ServerPlayer player) {
		return new Present(player);
	}

	public static PotentialPlayer get(UUID player) {
		return new Present(player);
	}

	public static PotentialPlayer empty() {
		return Absent.INSTANCE;
	}

	static final class Absent extends PotentialPlayer {

		public static final Absent INSTANCE = new Absent();
		public static final Codec<Absent> CODEC = Codec.unit(INSTANCE);

		private Absent() {}

		@Override
		public Optional<ServerPlayer> getPlayer(MinecraftServer server) {
			return Optional.empty();
		}
	}

	static final class Present extends PotentialPlayer {

		public static final Codec<Present> CODEC = UUIDUtil.LENIENT_CODEC.xmap(
				Present::new, p -> p.uuid
		);

		private final UUID uuid;
		private ServerPlayer player;

		public Present(UUID id) {
			this.uuid = id;
		}

		public Present(ServerPlayer player) {
			this(player.getUUID());
			this.player = player;
		}

		@Override
		public Optional<ServerPlayer> getPlayer(MinecraftServer server) {
			if (player == null || player.isRemoved()) {
				player = server.getPlayerList().getPlayer(uuid);
			}
			return Optional.ofNullable(player);
		}
	}

}
