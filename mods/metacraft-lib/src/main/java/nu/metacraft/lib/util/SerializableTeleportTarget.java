package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;

public record SerializableTeleportTarget(
		Optional<ResourceKey<Level>> dim, Vec3 pos, Vec3 velocity, float yaw, float pitch,
		boolean playPortalSound, boolean chunkload, Set<Relative> relatives
	) {

		public static final MapCodec<SerializableTeleportTarget> TELEPORT_TARGET_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Level.RESOURCE_KEY_CODEC.optionalFieldOf("world").orElse(Optional.of(Level.OVERWORLD)).forGetter(SerializableTeleportTarget::dim),
						Vec3.CODEC.fieldOf("position").forGetter(SerializableTeleportTarget::pos),
						Vec3.CODEC.fieldOf("velocity").forGetter(SerializableTeleportTarget::velocity),
						Codec.floatRange(-180, 180).fieldOf("yaw").forGetter(SerializableTeleportTarget::yaw),
						Codec.floatRange(-90, 90).fieldOf("pitch").forGetter(SerializableTeleportTarget::pitch),
						Codec.BOOL.optionalFieldOf("play_portal_sound", true).forGetter(SerializableTeleportTarget::playPortalSound),
						Codec.BOOL.optionalFieldOf("chunkload", false).forGetter(SerializableTeleportTarget::chunkload),
						METACodecs.POSITION_FLAG_SET_CODEC.optionalFieldOf("relatives", Set.of()).forGetter(SerializableTeleportTarget::relatives)
				).apply(instance, SerializableTeleportTarget::new)
		);

		private TeleportTransition.PostTeleportTransition getTransition() {
			TeleportTransition.PostTeleportTransition postDimensionTransition = TeleportTransition.DO_NOTHING;
			if (playPortalSound) {
				postDimensionTransition = postDimensionTransition.then(TeleportTransition.PLAY_PORTAL_SOUND);
			}
			if (chunkload) {
				postDimensionTransition = postDimensionTransition.then(TeleportTransition.PLACE_PORTAL_TICKET);
			}
			return postDimensionTransition;
		}

		public Optional<TeleportTransition> getFixedOrLocal(MinecraftServer server, ResourceKey<Level> fallbackDim) {
			return Optional.ofNullable(server.getLevel(dim.orElse(fallbackDim))).map(
					dim -> new TeleportTransition(
							dim, pos, velocity, yaw, pitch, relatives, getTransition()
					)
			);
		}

		public Optional<TeleportTransition> getIfFixed(MinecraftServer server) {
			return dim.map(server::getLevel).map(
				dim -> new TeleportTransition(
					dim, pos, velocity, yaw, pitch, relatives, getTransition()
				)
			);
		}

	}