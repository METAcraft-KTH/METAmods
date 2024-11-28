package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4f;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.SmoothEntityPathTranstion;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.util.Interpolatable;
import se.datasektionen.mc.cutscenes.util.InterpolationSetContainer;
import se.datasektionen.mc.cutscenes.util.Target;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public record SmoothEntityPathConfig(
		InterpolationSetContainer<DisplayEntityTarget> targets, int interpolationDuration, int teleportInterval,
		EntityRef entity
) implements TransitionConfig {

	public static final MapCodec<InterpolationSetContainer<DisplayEntityTarget>> SMOOTH_PATH = InterpolationSetContainer.createCodec(
			DisplayEntityTarget.CODEC, s -> DisplayEntityTarget.fromList((DoubleStream) s)
	);

	public static final MapCodec<SmoothEntityPathConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SMOOTH_PATH.forGetter(c -> c.targets),
					Codecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(SmoothEntityPathConfig::interpolationDuration),
					Codecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(SmoothEntityPathConfig::teleportInterval),
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(SmoothEntityPathConfig::entity)
			).apply(instance, SmoothEntityPathConfig::new)
	);

	public record DisplayEntityTarget(
			Target target, AffineTransformation transformation,
			float shadowRadius, float shadowStrength,
			int background, byte textOpacity //Only used on text displays.
	) implements Interpolatable {
		public static final MapCodec<DisplayEntityTarget> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Target.MAP_CODEC.forGetter(DisplayEntityTarget::target),
						AffineTransformation.CODEC.optionalFieldOf("transformation", AffineTransformation.identity()).forGetter(DisplayEntityTarget::transformation),
						Codec.FLOAT.optionalFieldOf("shadow_radius", 0.0f).forGetter(DisplayEntityTarget::shadowRadius),
						Codec.FLOAT.optionalFieldOf("shadow_strength", 1.0f).forGetter(DisplayEntityTarget::shadowStrength),
						Codec.INT.optionalFieldOf("background", DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND).forGetter(DisplayEntityTarget::background),
						Codec.BYTE.optionalFieldOf("text_opacity", (byte) -1).forGetter(DisplayEntityTarget::textOpacity)
				).apply(instance, DisplayEntityTarget::new)
		);

		public static final DisplayEntityTarget DEFAULT = new SmoothEntityPathConfig.DisplayEntityTarget(
				Target.DEFAULT,
				AffineTransformation.identity(),
				0, 0, DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND,  (byte) -1
		);

		public DisplayEntityTarget {
			transformation.getScale(); //Initialize internal variables in transformation to prevent crash when serializing.
		}

		private static final int TARGET_SIZE = 5;
		private static final int MATRIX_SIZE = 4*4;
		private static final int VARS_POS = TARGET_SIZE + MATRIX_SIZE;
		private static final int SIZE = TARGET_SIZE+MATRIX_SIZE+4;

		public static DisplayEntityTarget fromEntity(Entity entity) {
			var target = Target.fromEntity(entity);
			var data = entity.writeNbt(new NbtCompound());
			var transformation = AffineTransformation.identity();
			if (data.contains(DisplayEntity.TRANSFORMATION_NBT_KEY)) {
				transformation = AffineTransformation.ANY_CODEC.parse(NbtOps.INSTANCE, data.get(DisplayEntity.TRANSFORMATION_NBT_KEY)).resultOrPartial(
						Cutscenes.LOGGER::error
				).orElse(transformation);
			}
			float shadowRadius = 0;
			if (data.contains(DisplayEntity.SHADOW_RADIUS_NBT_KEY)) {
				shadowRadius = data.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY);
			}
			float shadowStrength = 1;
			if (data.contains(DisplayEntity.SHADOW_STRENGTH_NBT_KEY)) {
				shadowStrength = data.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY);
			}

			int background = DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND;
			if (data.contains("background")) {
				background = data.getInt("background");
			}

			byte textOpacity = -1;
			if (data.contains("text_opacity")) {
				textOpacity = data.getByte("text_opacity");
			}
			return new DisplayEntityTarget(
					target, transformation, shadowRadius, shadowStrength, background, textOpacity
			);
		}

		public static DisplayEntityTarget fromList(DoubleStream stream) {
			var array = stream.limit(SIZE).toArray();
			var target = Target.fromList(Arrays.stream(array));
			float[] mat = new float[MATRIX_SIZE];
			for (int i = 0; i < mat.length; i++) {
				mat[i] = (float) array[i+5];
			}
			Matrix4f matrix = new Matrix4f();
			matrix.set(mat);
			var transformation = new AffineTransformation(matrix);

			var shadowRadius = array[VARS_POS];
			var shadowStrength = array[VARS_POS + 1];
			var background = array[VARS_POS + 2];
			var textOpacity = array[VARS_POS + 3];

			return new DisplayEntityTarget(
					target, transformation, (float) shadowRadius, (float) shadowStrength,
					(int) Math.round(background), (byte) Math.round(textOpacity)
			);
		}

		@Override
		public DoubleList getValues() {
			var list = new DoubleArrayList(SIZE);
			list.addAll(target.getValues());

			float[] array = new float[MATRIX_SIZE];
			transformation.getMatrix().get(array);
			for (float f : array) {
				list.add(f);
			}

			list.add(shadowRadius);
			list.add(shadowStrength);
			list.add(background);
			list.add(textOpacity);

			return list;
		}
	}

	@Override
	public Transition create() {
		return new SmoothEntityPathTranstion(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SMOOTH_ENTITY_PATH;
	}

}
