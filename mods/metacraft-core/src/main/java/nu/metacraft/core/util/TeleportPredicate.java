package nu.metacraft.core.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public record TeleportPredicate(EntityPredicate entityPredicate, boolean result) {
	public static final Codec<TeleportPredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			EntityPredicate.CODEC.fieldOf("entity_predicate").forGetter(TeleportPredicate::entityPredicate),
			Codec.BOOL.fieldOf("result").forGetter(TeleportPredicate::result)
		).apply(instance, TeleportPredicate::new)
	);
	public static final Codec<List<TeleportPredicate>> LIST_CODEC = CODEC.listOf();


	public static boolean shouldTeleport(
			List<TeleportPredicate> shouldTeleport, ServerLevel world, Entity entity
	) {
		for (var entry : shouldTeleport) {
			if (entry.entityPredicate().matches(world, null, entity)) {
				return entry.result();
			}
		}
		return true;
	}
}
