package se.datasektionen.mc.metacraft_core.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

public record TeleportPredicate(EntityPredicate entityPredicate, boolean result) {
	public static final Codec<TeleportPredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			EntityPredicate.CODEC.fieldOf("entity_predicate").forGetter(TeleportPredicate::entityPredicate),
			Codec.BOOL.fieldOf("result").forGetter(TeleportPredicate::result)
		).apply(instance, TeleportPredicate::new)
	);
	public static final Codec<List<TeleportPredicate>> LIST_CODEC = CODEC.listOf();


	public static boolean shouldTeleport(
			List<TeleportPredicate> shouldTeleport, ServerWorld world, Entity entity
	) {
		for (var entry : shouldTeleport) {
			if (entry.entityPredicate().test(world, null, entity)) {
				return entry.result();
			}
		}
		return true;
	}
}
