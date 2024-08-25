package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

import java.util.Optional;

public class TeleportTransition extends InstantTransition {

	public static final MapCodec<TeleportTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SerializableTeleportTarget.TELEPORT_TARGET_CODEC.forGetter(t -> t.teleportTarget)
			).apply(instance, TeleportTransition::new)
	);

	private final SerializableTeleportTarget teleportTarget;

	public TeleportTransition(SerializableTeleportTarget teleportTarget) {
		this.teleportTarget = teleportTarget;
	}

	public Optional<TeleportTarget> getTarget(CutsceneInstance cutscene) {
		return teleportTarget.getTeleportTarget(cutscene.getServer(), cutscene.getCutsceneWorld().getRegistryKey());
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		getTarget(cutscene).ifPresent(
				target -> cutscene.setTargetWorld(target.world())
		);
	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		getTarget(cutscene).ifPresent(
				target -> ((EntityExtension) player).metacraft$teleportInCutscene(target)
		);
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.TELEPORT_TRANSITION;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.TELEPORT_TRANSITION;
	}

	public record SerializableTeleportTarget(
			Optional<RegistryKey<World>> dim, Vec3d pos, Vec3d velocity, float yaw, float pitch,
			boolean playPortalSound, boolean chunkload
	) {

		public static final MapCodec<SerializableTeleportTarget> TELEPORT_TARGET_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						World.CODEC.optionalFieldOf("world").orElse(Optional.of(World.OVERWORLD)).forGetter(SerializableTeleportTarget::dim),
						Vec3d.CODEC.fieldOf("position").forGetter(SerializableTeleportTarget::pos),
						Vec3d.CODEC.fieldOf("velocity").forGetter(SerializableTeleportTarget::velocity),
						Codec.floatRange(-180, 180).fieldOf("yaw").forGetter(SerializableTeleportTarget::yaw),
						Codec.floatRange(-90, 90).fieldOf("pitch").forGetter(SerializableTeleportTarget::pitch),
						Codec.BOOL.optionalFieldOf("play_portal_sound", true).forGetter(SerializableTeleportTarget::playPortalSound),
						Codec.BOOL.optionalFieldOf("chunkload", false).forGetter(SerializableTeleportTarget::chunkload)
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

		public Optional<TeleportTarget> getTeleportTarget(MinecraftServer server, RegistryKey<World> fallbackDim) {
			return Optional.ofNullable(server.getWorld(dim.orElse(fallbackDim))).map(
					dim -> new TeleportTarget(
							dim, pos, velocity, yaw, pitch, getTransition()
					)
			);
		}
	}
}
