package nu.metacraft.revival.predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record RevivalPredicate(
		Optional<Boolean> unconscious,
		Optional<Boolean> menuOpen,
		Optional<IntRange> timeUntilDeath,
		Optional<IntRange> timeUntilRevival,
		Optional<EntityPredicate> reviver
) implements EntitySubPredicate {

	public static final MapCodec<RevivalPredicate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.optionalFieldOf("unconscious").forGetter(RevivalPredicate::unconscious),
					Codec.BOOL.optionalFieldOf("menu_open").forGetter(RevivalPredicate::menuOpen),
					IntRange.CODEC.optionalFieldOf("time_until_death").forGetter(RevivalPredicate::timeUntilDeath),
					IntRange.CODEC.optionalFieldOf("time_until_revival").forGetter(RevivalPredicate::timeUntilRevival),
					EntityPredicate.CODEC.optionalFieldOf("reviver").forGetter(RevivalPredicate::reviver)
			).apply(instance, RevivalPredicate::new)
	);

	@Override
	public @NotNull MapCodec<? extends EntitySubPredicate> codec() {
		return CODEC;
	}

	private boolean needsLootContext() {
		return timeUntilDeath.isPresent() || timeUntilRevival.isPresent();
	}

	@Override
	public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
		if (entity instanceof ServerPlayerExtension ext) {
			if (unconscious.isPresent() && ext.metacraft$isUnconscious() != unconscious.get()) {
				return false;
			}
			if (menuOpen.isPresent() && ext.metacraft$isRevivalMenuOpen() != menuOpen.get()) {
				return false;
			}
			if (needsLootContext()) {
				LootParams lootParams = new LootParams.Builder((ServerLevel) entity.level())
						.withParameter(LootContextParams.ORIGIN, entity.position())
						.withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
						.create(LootContextParamSets.ADVANCEMENT_ENTITY);
				LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
				if (timeUntilDeath.isPresent() && !timeUntilDeath.get().test(lootContext, ext.metacraft$getTimeUntilDeath())) {
					return false;
				}
				if (timeUntilRevival.isPresent() && !timeUntilRevival.get().test(lootContext, ext.metacraft$getTimeUntilRevival())) {
					return false;
				}
			}
			if (reviver.isPresent() && !reviver.get().matches(level, position, ext.metacraft$getReviver())) {
				return false;
			}
			return true;
		}
		return false;
	}
}
