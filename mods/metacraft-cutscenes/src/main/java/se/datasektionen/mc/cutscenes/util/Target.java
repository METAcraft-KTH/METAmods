package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public record Target(Vec3d pos, float yaw, float pitch) {

	public static final Codec<Target> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Vec3d.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					Codec.FLOAT.fieldOf("yaw").forGetter(t -> t.yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(t -> t.pitch)
			).apply(instance, Target::new)
	);

	public static Target fromPlayer(ServerPlayerEntity player) {
		return new Target(player.getPos(), player.getYaw(), player.getPitch());
	}

}
