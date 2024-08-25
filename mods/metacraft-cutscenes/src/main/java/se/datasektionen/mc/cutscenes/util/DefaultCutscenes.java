package se.datasektionen.mc.cutscenes.util;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.JavaOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.world.GameMode;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.CutsceneRef;
import se.datasektionen.mc.cutscenes.entity_ref.SelfRef;
import se.datasektionen.mc.cutscenes.position_ref.AtEntityRef;
import se.datasektionen.mc.cutscenes.transitions.SetGameModeTransition;
import se.datasektionen.mc.cutscenes.transitions.entity.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultCutscenes {

	public static final Cutscene CREEPER_KILL_PIGLIN = new Cutscene(new IntervalMap<>(List.of(
				new IntervalMap.Interval<>(
						0, 300, new SetGameModeTransition(
								GameMode.SPECTATOR
						)
				),
				new IntervalMap.Interval<>(
						0, 300, new SpawnEntity(
								List.of("zombie"),
								new AtEntityRef(SelfRef.getInstance(), Vec3d.ZERO, new Vec3d(5, 0, 3), false),
								NbtCompound.CODEC.parse(JavaOps.INSTANCE, Map.of("id", "piglin")).getOrThrow(), Optional.empty(),
								false
						)
				),
				new IntervalMap.Interval<>(
						0, 300, new SpawnEntity(
								List.of("creeper"),
								new AtEntityRef(SelfRef.getInstance(), Vec3d.ZERO, new Vec3d(-5, 0, 3), false),
								NbtCompound.CODEC.parse(JavaOps.INSTANCE, Map.of("id", "creeper")).getOrThrow(), Optional.empty(),
								false
						)
				),
				new IntervalMap.Interval<>(
						100, 200, new MoveTo(
								new CutsceneRef("zombie"), new AtEntityRef(new CutsceneRef("creeper"), Vec3d.ZERO, Vec3d.ZERO, false),
								1
						)
				),
				new IntervalMap.Interval<>(
						100, 200, new LookAt(
								new CutsceneRef("creeper"), new AtEntityRef(new CutsceneRef("zombie"), Vec3d.ZERO, Vec3d.ZERO, true),
								Optional.empty(), Optional.empty()
						)
				),
				new IntervalMap.Interval<>(
						200, 300, new Attack(
								new CutsceneRef("creeper"), new CutsceneRef("zombie")
						)
				),
				new IntervalMap.Interval<>(
						100, 300, new DisableAIConfig(new CutsceneRef("zombie"), false)
				),
				new IntervalMap.Interval<>(
						100, 300, new DisableAIConfig(new CutsceneRef("creeper"), true)
				),
				new IntervalMap.Interval<>(
						0, 300, new DisableAIConfig(new CutsceneRef(CutsceneInstance.PLAYER_REFERENCE), false)
				),
				new IntervalMap.Interval<>(
						150, 250, new RotateHead.RotateHeadConfig(
								new CutsceneRef(CutsceneInstance.PLAYER_REFERENCE),
								ImmutableList.of(
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(-10),
												ConstantFloatProvider.create(10),
												0.1f
										),
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(10),
												ConstantFloatProvider.create(10),
												0.3f
										),
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(-10),
												ConstantFloatProvider.create(10),
												0.5f
										),
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(10),
												ConstantFloatProvider.create(10),
												0.7f
										),
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(-10),
												ConstantFloatProvider.create(10),
												0.9f
										),
										new RotateHead.RotateHeadConfig.OffsetTarget(
												ConstantFloatProvider.create(0),
												ConstantFloatProvider.create(0),
												1
										)
								)
						)
				)
		)),
		true, true, true,
		true, Optional.empty()
	);

}
