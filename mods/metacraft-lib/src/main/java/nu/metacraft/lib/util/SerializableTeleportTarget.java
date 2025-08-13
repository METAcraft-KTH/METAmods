package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Set;

public record SerializableTeleportTarget(
		Optional<RegistryKey<World>> dim, Vec3d pos, Vec3d velocity, float yaw, float pitch,
		boolean playPortalSound, boolean chunkload, Set<PositionFlag> relatives
	) {

		public static final MapCodec<SerializableTeleportTarget> TELEPORT_TARGET_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						World.CODEC.optionalFieldOf("world").orElse(Optional.of(World.OVERWORLD)).forGetter(SerializableTeleportTarget::dim),
						Vec3d.CODEC.fieldOf("position").forGetter(SerializableTeleportTarget::pos),
						Vec3d.CODEC.fieldOf("velocity").forGetter(SerializableTeleportTarget::velocity),
						Codec.floatRange(-180, 180).fieldOf("yaw").forGetter(SerializableTeleportTarget::yaw),
						Codec.floatRange(-90, 90).fieldOf("pitch").forGetter(SerializableTeleportTarget::pitch),
						Codec.BOOL.optionalFieldOf("play_portal_sound", true).forGetter(SerializableTeleportTarget::playPortalSound),
						Codec.BOOL.optionalFieldOf("chunkload", false).forGetter(SerializableTeleportTarget::chunkload),
						ExtraCodecs.POSITION_FLAG_SET_CODEC.optionalFieldOf("relatives", Set.of()).forGetter(SerializableTeleportTarget::relatives)
				).apply(instance, SerializableTeleportTarget::new)
		);

		private TeleportTarget.PostDimensionTransition getTransition() {
			TeleportTarget.PostDimensionTransition postDimensionTransition = TeleportTarget.NO_OP;
			if (playPortalSound) {
				postDimensionTransition = postDimensionTransition.then(TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET);
			}
			if (chunkload) {
				postDimensionTransition = postDimensionTransition.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
			}
			return postDimensionTransition;
		}

		public Optional<TeleportTarget> getFixedOrLocal(MinecraftServer server, RegistryKey<World> fallbackDim) {
			return Optional.ofNullable(server.getWorld(dim.orElse(fallbackDim))).map(
					dim -> new TeleportTarget(
							dim, pos, velocity, yaw, pitch, relatives, getTransition()
					)
			);
		}

		public Optional<TeleportTarget> getIfFixed(MinecraftServer server) {
			return dim.map(server::getWorld).map(
				dim -> new TeleportTarget(
					dim, pos, velocity, yaw, pitch, relatives, getTransition()
				)
			);
		}

	}