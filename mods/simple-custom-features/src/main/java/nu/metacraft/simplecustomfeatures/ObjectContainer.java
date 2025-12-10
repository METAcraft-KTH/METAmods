package nu.metacraft.simplecustomfeatures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;

import java.util.*;
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
						ExtraCodecs.JAVA.fieldOf(OBJECT).forGetter(o -> o.rawObject)
				).apply(instance, Deferred::new)
		);

		private final Object rawObject;
		private Optional<Loaded<?>> partial;

		public Deferred(Identifier id, Object rawObject) {
			super(id);
			this.rawObject = rawObject;
			this.partial = BaseObject.REGISTRY_CODEC.parse(
					LenientJavaOps.INSTANCE, rawObject
			).resultOrPartial().map(partial -> new Loaded<>(id, partial));
		}

		private String parseErrorStart() {
			return "Unable to parse " + rawObject;
		}

		@Override
		public DataResult<? extends ObjectType<?, ?>> getType() {
			if (rawObject instanceof Map<?,?> map) {
				var type = map.get("type");
				if (type instanceof String key) {
					return Optional.ofNullable(ObjectRegistry.REGISTRY.getValue(Identifier.tryParse(key))).map(
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
					opsApplier.apply(LenientJavaOps.INSTANCE), rawObject
			);
		}

		public DataResult<Loaded<?>> load() {
			return tryParse(ops -> ops).map(o -> new Loaded<>(id, o));
		}

		public DataResult<Loaded<?>> load(HolderLookup.Provider lookup) {
			return tryParse(lookup::createSerializationContext).map(o -> new Loaded<>(id, o));
		}

		public Optional<Loaded<?>> getPartial() {
			return partial;
		}

		private Loaded<?> removePartial() {
			var partial = this.partial.orElseThrow();
			this.partial = Optional.empty();
			return partial;
		}

		public static void removePartials(Stream<Deferred> toRemove) {
			Loaded.unregister(toRemove.filter(def -> def.getPartial().isPresent()).map(Deferred::removePartial));
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
		private final Multimap<Identifier, Child<?>> children = HashMultimap.create();

		public Loaded(Identifier id, BaseObject<T> object) {
			super(id);
			this.object = object;
		}

		private static <T> void register(Identifier id, BaseObject<T> baseObject, Consumer<T> onSuccess, @Nullable HolderLookup.Provider lookup) {
			var key = ResourceKey.create(baseObject.getType().getRegistry().key(), id);
			baseObject.createObject(key, lookup).resultOrPartial(
					message -> {
						if (!message.startsWith(BaseObject.NO_ERROR_PREFIX) || FabricLoader.getInstance().isDevelopmentEnvironment()) {
							Features.LOGGER.error("Unable to create {}", id);
							Features.LOGGER.error(message);
						}
					}
			).ifPresent(object -> {
				if (!baseObject.getType().getRegistry().containsKey(key)) {
					var ref = Registry.registerForHolder(baseObject.getType().getRegistry(), key, object);
					baseObject.onRegistrationSuccess(ref);
					onSuccess.accept(object);
				} else {
					baseObject.onRegistrationFail(id, object);
					Features.LOGGER.error(id + " is already registered, skipping it.");
				}
			});
		}

		private static <T> void unregister(BaseObject<T> baseObject, T object) {
			baseObject.onUnregister(baseObject.getType().getRegistry().wrapAsHolder(object));
			((RegistryExtensions) baseObject.getType().getRegistry()).simpleCustomFeatures$remove(
					object
			);
		}

		private <S> void registerChild(Identifier id, BaseObject<S> baseObject, @Nullable HolderLookup.Provider lookup) {
			register(id, baseObject, object -> {
				children.put(id, new Child<>(baseObject, object));
			}, lookup);
		}

		private static  <S> void unregisterChild(Child<S> object) {
			unregister(object.baseObject, object.object);
		}

		private void register(@Nullable HolderLookup.Provider lookup) {
			if (actualObject == null) {
				register(id, object, object -> {
					this.actualObject = object;
				}, lookup);
				object.createChildren(this).forEach((id, object) -> this.registerChild(id, object, lookup));
			}
		}

		private void unregister() {
			if (actualObject != null) {
				unregister(object, actualObject);
				children.forEach((id, object) -> {
					unregisterChild(object);
				});
				children.clear();
				this.actualObject = null;
			}
		}

		@Override
		public DataResult<? extends ObjectType<?, ?>> getType() {
			return DataResult.success(object.getType());
		}

		public BaseObject<T> getObject() {
			return object;
		}

		public T getActualObject() {
			return actualObject;
		}

		public Multimap<Identifier, Child<?>> getChildren() {
			return children;
		}

		public record Child<T>(BaseObject<T> baseObject, T object) {}
	}

	protected static void applyRegistryChanges(Stream<Loaded<?>> loadedEntries, Consumer<Loaded<?>> applier) {
		Set<Registry<?>> registriesToLock = new HashSet<>();
		loadedEntries.forEach(entry -> {
			if (RegistryHelper.unlockRegistry(entry.getObject().getType().getRegistry())) {
				registriesToLock.add(entry.getObject().getType().getRegistry());
			}
			for (var registry : entry.getObject().getChildrenRegistries()) {
				if (RegistryHelper.unlockRegistry(registry)) {
					registriesToLock.add(registry);
				}
			}
			applier.accept(entry);
		});
		for (var reg : registriesToLock) {
			RegistryHelper.lockRegistry(reg);
		}
	}

	public static void register(Stream<Loaded<?>> loadedEntries, @Nullable HolderLookup.Provider lookup) {
		applyRegistryChanges(loadedEntries, object -> object.register(lookup));
	}

	public static void unregister(Stream<Loaded<?>> loadedEntries) {
		applyRegistryChanges(loadedEntries, Loaded::unregister);
	}
}
