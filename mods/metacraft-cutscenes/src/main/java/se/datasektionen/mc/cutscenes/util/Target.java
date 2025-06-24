package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;

import java.util.stream.DoubleStream;

public record Target(Vec3d pos, float yaw, float pitch) implements Interpolatable<CutsceneContext> {

	public static final Int2ObjectMap<InterpolationSet.Adjuster> ADJUSTER = Int2ObjectMaps.singleton(
			3, InterpolationSet::fixYawRotations
	);

	public static final MapCodec<Target> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3d.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					Codec.FLOAT.fieldOf("yaw").forGetter(t -> t.yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(t -> t.pitch)
			).apply(instance, Target::new)
	);

	public static final Codec<Target> CODEC = MAP_CODEC.codec();

	public static final Target DEFAULT = new Target(Vec3d.ZERO, 0, 0);

	public static Target fromEntity(Entity entity) {
		return new Target(entity.getPos(), entity.getYaw(), entity.getPitch());
	}

	public static Target fromList(DoubleStream stream) {
		var list = stream.limit(5).toArray();
		double x = list[0];
		double y = list[1];
		double z = list[2];
		float yaw = (float) list[3];
		float pitch = (float) list[4];
		return new Target(new Vec3d(x, y, z), yaw, pitch);
	}

	@Override
	public DoubleList getValues(@Nullable CutsceneContext ctx) {
		return DoubleList.of(pos.x, pos.y, pos.z, yaw, pitch);
	}
}
