package se.datasektionen.mc.simplecustomfeatures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract sealed class ObjectContainer permits ObjectContainer.Deferred, ObjectContainer.Loaded {

	public static final Codec<ObjectContainer> CODEC = Codec.lazyInitialized(() -> Codec.either(
			Loaded.LOADED_CODEC,
			Deferred.DEFERRED_CODEC
	).xmap(
			Either::unwrap,
			container -> switch (container) {
				case Deferred d -> Either.right(d);
				case Loaded<?> l -> Either.left(l);
			}
	));

	private static final String ID = "id";
	private static final String OBJECT = "object";

	protected final Identifier id;

	public ObjectContainer(Identifier id) {
		this.id = id;
	}

	public abstract DataResult<? extends ObjectType<?, ?>> getType();

	public Identifier getID() {
		return id;
	}

	public static final class Deferred extends ObjectContainer {

		public static final Codec<Deferred> DEFERRED_CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Identifier.CODEC.fieldOf(ID).forGetter(o -> o.id),
						Codecs.BASIC_OBJECT.fieldOf(OBJECT).forGetter(o -> o.rawObject)
				).apply(instance, Deferred::new)
		);

		private final Object rawObject;

		public Deferred(Identifier id, Object rawObject) {
			super(id);
			this.rawObject = rawObject;
		}

		private String parseErrorStart() {
			return "Unable to parse " + rawObject;
		}

		@Override
		public DataResult<? extends ObjectType<?, ?>> getType() {
			if (rawObject instanceof Map<?,?> map) {
				var type = map.get("type");
				if (type instanceof String key) {
					return Optional.ofNullable(ObjectRegistry.REGISTRY.get(Identifier.tryParse(key))).map(
							DataResult::success
					).orElse(DataResult.error(() -> parseErrorStart() + ", " + type + " is not a valid registered object type"));
				} else {
					return DataResult.error(() -> parseErrorStart() + ", " + type + " is not a valid string");
				}
			} else {
				return DataResult.error(() -> parseErrorStart() + " because it is not a valid object");
			}
		}

		private DataResult<BaseObject<?>> tryParse(UnaryOperator<DynamicOps<Object>> opsApplier) {
			return BaseObject.REGISTRY_CODEC.parse(
					opsApplier.apply(JavaOps.INSTANCE), rawObject
			);
		}

		public DataResult<Loaded<?>> load() {
			return tryParse(ops -> ops).map(o -> new Loaded<>(id, o));
		}

		public DataResult<Loaded<?>> load(RegistryWrapper.WrapperLookup lookup) {
			return tryParse(lookup::getOps).map(o -> new Loaded<>(id, o));
		}
	}

	public static final class Loaded<T> extends ObjectContainer {

		public static final Codec<Loaded<?>> LOADED_CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Identifier.CODEC.fieldOf(ID).forGetter(o -> o.id),
						BaseObject.REGISTRY_CODEC.fieldOf(OBJECT).forGetter(o -> o.object)
				).apply(instance, Loaded::new)
		);

		private final BaseObject<T> object;
		private T actualObject;

		public Loaded(Identifier id, BaseObject<T> object) {
			super(id);
			this.object = object;
		}

		private void register() {
			if (actualObject == null) {
				object.createObject().resultOrPartial(
						message -> {
							Features.LOGGER.error("Unable to create {}", id);
							Features.LOGGER.error(message);
						}
				).ifPresent(object -> {
					if (!this.object.getType().getRegistry().containsId(id)) {
						var ref = Registry.registerReference(this.object.getType().getRegistry(), id, object);
						this.object.onRegistrationSuccess(ref);
						actualObject = object;
					} else {
						this.object.onRegistrationFail(object);
						Features.LOGGER.error(id + " is already registered, skipping it.");
					}
				});
			}
		}

		private void unregister() {
			if (actualObject != null) {
				this.object.onUnregister(object.getType().getRegistry().getEntry(actualObject));
				((RegistryExtensions) object.getType().getRegistry()).simpleCustomFeatures$remove(
						actualObject
				);
				actualObject = null;
			}
		}

		@Override
		public DataResult<? extends ObjectType<?, ?>> getType() {
			return DataResult.success(object.getType());
		}

		public BaseObject<T> getObject() {
			return object;
		}
	}

	protected static void applyRegistryChanges(Stream<Loaded<?>> loadedEntries, Consumer<Loaded<?>> applier) {
		Set<Registry<?>> registriesToLock = new HashSet<>();
		loadedEntries.forEach(entry -> {
			if (RegistryHelper.unlockRegistry(entry.getObject().getType().getRegistry())) {
				registriesToLock.add(entry.getObject().getType().getRegistry());
			}
			applier.accept(entry);
		});
		for (var reg : registriesToLock) {
			RegistryHelper.lockRegistry(reg);
		}
	}

	public static void register(Stream<Loaded<?>> loadedEntries) {
		applyRegistryChanges(loadedEntries, Loaded::register);
	}

	public static void unregister(Stream<Loaded<?>> loadedEntries) {
		applyRegistryChanges(loadedEntries, Loaded::unregister);
	}
}
