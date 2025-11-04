package nu.metacraft.cutscenes.cutscene.world;

import com.google.common.collect.Iterables;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

public class CombinedEntityLookup implements LevelEntityGetter<Entity> {

	private final List<LevelEntityGetter<Entity>> lookups;

	public CombinedEntityLookup(List<LevelEntityGetter<Entity>> lookups) {
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
	public Iterable<Entity> getAll() {
		return Iterables.concat((Iterable<Iterable<Entity>>) () -> lookups.stream().map(LevelEntityGetter::getAll).iterator());
	}

	@Override
	public <U extends Entity> void get(EntityTypeTest<Entity, U> filter, AbortableIterationConsumer<U> consumer) {
		lookups.forEach(lookup -> lookup.get(filter, consumer));
	}

	@Override
	public void get(AABB box, Consumer<Entity> action) {
		lookups.forEach(lookup -> lookup.get(box, action));
	}

	@Override
	public <U extends Entity> void get(EntityTypeTest<Entity, U> filter, AABB box, AbortableIterationConsumer<U> consumer) {
		lookups.forEach(lookup -> lookup.get(filter, box, consumer));
	}
}
