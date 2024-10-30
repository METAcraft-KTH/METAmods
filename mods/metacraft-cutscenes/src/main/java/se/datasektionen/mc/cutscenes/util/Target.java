package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.stream.DoubleStream;

public record Target(Vec3d pos, float yaw, float pitch) implements Interpolatable {

	public static final MapCodec<Target> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3d.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					Codec.FLOAT.fieldOf("yaw").forGetter(t -> t.yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(t -> t.pitch)
			).apply(instance, Target::new)
	);

	public static final Codec<Target> CODEC = MAP_CODEC.codec();

	public static Target fromPlayer(ServerPlayerEntity player) {
		return new Target(player.getPos(), player.getYaw(), player.getPitch());
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
	public DoubleList getValues() {
		return DoubleList.of(pos.x, pos.y, pos.z, yaw, pitch);
	}
}
