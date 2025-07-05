package nu.metacraft.cutscenes.cutscene.world;

import com.google.common.collect.Iterables;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLookup;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class CombinedEntityLookup implements EntityLookup<Entity> {

	private final List<EntityLookup<Entity>> lookups;

	public CombinedEntityLookup(List<EntityLookup<Entity>> lookups) {
		this.lookups = lookups;
	}

	@Nullable
	@Override
	public Entity get(int id) {
		return lookups.stream().flatMap(lookup -> Optional.ofNullable(lookup.get(id)).stream()).findFirst().orElse(null);
	}

	@Nullable
	@Override
	public Entity get(UUID uuid) {
		return lookups.stream().flatMap(lookup -> Optional.ofNullable(lookup.get(uuid)).stream()).findFirst().orElse(null);
	}

	@Override
	public Iterable<Entity> iterate() {
		return Iterables.concat((Iterable<Iterable<Entity>>) () -> lookups.stream().map(EntityLookup::iterate).iterator());
	}

	@Override
	public <U extends Entity> void forEach(TypeFilter<Entity, U> filter, LazyIterationConsumer<U> consumer) {
		lookups.forEach(lookup -> lookup.forEach(filter, consumer));
	}

	@Override
	public void forEachIntersects(Box box, Consumer<Entity> action) {
		lookups.forEach(lookup -> lookup.forEachIntersects(box, action));
	}

	@Override
	public <U extends Entity> void forEachIntersects(TypeFilter<Entity, U> filter, Box box, LazyIterationConsumer<U> consumer) {
		lookups.forEach(lookup -> lookup.forEachIntersects(filter, box, consumer));
	}
}
