package nu.metacraft.cutscenes.transitions;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.util.SerializableTeleportTarget;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.extension.EntityExtension;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.core.rotation_ref.RotationRef;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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

	public Optional<net.minecraft.world.level.portal.TeleportTransition> getTarget(CutsceneInstance cutscene) {
		return teleportTarget.getFixedOrLocal(cutscene.getServer(), cutscene.getCutsceneWorld().dimension());
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		getTarget(cutscene).ifPresent(
				target -> cutscene.setTargetWorld(target.newLevel())
		);
	}

	@Override
	public void activate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
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

	public record SerializableTeleportTargetDynamic(
			Optional<ResourceKey<Level>> dim, PositionRef pos, Vec3 velocity, RotationRef rot,
			boolean playPortalSound, boolean chunkload
	) {

		public static final MapCodec<SerializableTeleportTargetDynamic> TELEPORT_TARGET_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Level.RESOURCE_KEY_CODEC.optionalFieldOf("world").orElse(Optional.of(Level.OVERWORLD)).forGetter(SerializableTeleportTargetDynamic::dim),
						PositionRefRegistry.CODEC.fieldOf("pos").forGetter(SerializableTeleportTargetDynamic::pos),
						Vec3.CODEC.fieldOf("velocity").forGetter(SerializableTeleportTargetDynamic::velocity),
						RotationRefRegistry.CODEC.fieldOf("rot").forGetter(SerializableTeleportTargetDynamic::rot),
						Codec.BOOL.optionalFieldOf("play_portal_sound", true).forGetter(SerializableTeleportTargetDynamic::playPortalSound),
						Codec.BOOL.optionalFieldOf("chunkload", false).forGetter(SerializableTeleportTargetDynamic::chunkload)
				).apply(instance, SerializableTeleportTargetDynamic::new)
		);

		private net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition getTransition() {
			net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition postDimensionTransition = net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING;
			if (playPortalSound) {
				postDimensionTransition = postDimensionTransition.then(net.minecraft.world.level.portal.TeleportTransition.PLAY_PORTAL_SOUND);
			}
			if (chunkload) {
				postDimensionTransition = postDimensionTransition.then(net.minecraft.world.level.portal.TeleportTransition.PLACE_PORTAL_TICKET);
			}
			return postDimensionTransition;
		}

		public Optional<net.minecraft.world.level.portal.TeleportTransition> getTeleportTarget(@Nullable ServerPlayer player, CutsceneInstance cutscene) {
			return Optional.ofNullable(cutscene.getServer().getLevel(dim.orElse(cutscene.getDim()))).flatMap(
					dim -> pos().get(cutscene.createRefContext(player)).map(pos -> {
						var rot = rot().get(cutscene.createRefContext(player)).orElse(player != null ? player.getRotationVector() : Vec2.ZERO);
						return new net.minecraft.world.level.portal.TeleportTransition(
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

		public Optional<net.minecraft.world.level.portal.TeleportTransition> getTeleportTarget(@Nullable ServerPlayer player, CutsceneInstance cutscene) {
			return target.map(
					t -> t.getFixedOrLocal(cutscene.getServer(), cutscene.getDim()),
					t -> t.getTeleportTarget(player, cutscene)
			);
		}

		public Optional<ResourceKey<Level>> getDim() {
			return target.map(
					SerializableTeleportTarget::dim,
					TeleportTransition.SerializableTeleportTargetDynamic::dim
			);
		}
	}
}
