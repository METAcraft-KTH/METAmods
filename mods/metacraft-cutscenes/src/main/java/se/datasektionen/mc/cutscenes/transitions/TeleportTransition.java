package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;
import se.datasektionen.mc.cutscenes.rotation_ref.RotationRef;
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

	public record SerializableTeleportTargetDynamic(
			Optional<RegistryKey<World>> dim, PositionRef pos, Vec3d velocity, RotationRef rot,
			boolean playPortalSound, boolean chunkload
	) {

		public static final MapCodec<SerializableTeleportTargetDynamic> TELEPORT_TARGET_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						World.CODEC.optionalFieldOf("world").orElse(Optional.of(World.OVERWORLD)).forGetter(SerializableTeleportTargetDynamic::dim),
						PositionRefRegistry.CODEC.fieldOf("pos").forGetter(SerializableTeleportTargetDynamic::pos),
						Vec3d.CODEC.fieldOf("velocity").forGetter(SerializableTeleportTargetDynamic::velocity),
						RotationRefRegistry.CODEC.fieldOf("rot").forGetter(SerializableTeleportTargetDynamic::rot),
						Codec.BOOL.optionalFieldOf("play_portal_sound", true).forGetter(SerializableTeleportTargetDynamic::playPortalSound),
						Codec.BOOL.optionalFieldOf("chunkload", false).forGetter(SerializableTeleportTargetDynamic::chunkload)
				).apply(instance, SerializableTeleportTargetDynamic::new)
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

		public Optional<TeleportTarget> getTeleportTarget(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
			return Optional.ofNullable(cutscene.getServer().getWorld(dim.orElse(cutscene.getDim()))).flatMap(
					dim -> pos().get(cutscene.createRefContext(player)).map(pos -> {
						var rot = rot().get(cutscene.createRefContext(player)).orElse(player != null ? player.getRotationClient() : Vec2f.ZERO);
						return new TeleportTarget(
								dim, pos, velocity, rot.y, rot.x, getTransition()
						);
					})
			);
		}
	}

	public record SerializableTeleportTargetBoth(Either<SerializableTeleportTarget, SerializableTeleportTargetDynamic> target) {
		public static final Codec<SerializableTeleportTargetBoth> CODEC = Codec.either(
				SerializableTeleportTarget.TELEPORT_TARGET_CODEC.codec(),
				SerializableTeleportTargetDynamic.TELEPORT_TARGET_CODEC.codec()
		).xmap(SerializableTeleportTargetBoth::new, SerializableTeleportTargetBoth::target);

		public Optional<TeleportTarget> getTeleportTarget(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
			return target.map(
					t -> t.getTeleportTarget(cutscene.getServer(), cutscene.getDim()),
					t -> t.getTeleportTarget(player, cutscene)
			);
		}

		public Optional<RegistryKey<World>> getDim() {
			return target.map(
					TeleportTransition.SerializableTeleportTarget::dim,
					TeleportTransition.SerializableTeleportTargetDynamic::dim
			);
		}
	}
}
