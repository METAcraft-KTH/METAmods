package se.datasektionen.mc.simplecustomfeatures.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.simplecustomfeatures.RegistryExtensions;

import java.util.*;

@Mixin(SimpleRegistry.class)
public abstract class MixinSimpleRegistry<T> implements MutableRegistry<T>, RegistryExtensions<T> {

	@Shadow public abstract RegistryKey<? extends Registry<T>> getKey();

	@Shadow public abstract boolean containsId(Identifier id);

	@Shadow private boolean frozen;
	@Shadow private @Nullable Map<T, RegistryEntry.Reference<T>> intrusiveValueToEntry;
	@Shadow @Final private Map<T, RegistryEntry.Reference<T>> valueToEntry;

	@Shadow public abstract Optional<RegistryKey<T>> getKey(T entry);

	@Shadow @Final private Map<RegistryKey<T>, RegistryEntry.Reference<T>> keyToEntry;
	@Shadow @Final private Map<Identifier, RegistryEntry.Reference<T>> idToEntry;
	@Shadow @Final private ObjectList<RegistryEntry.Reference<T>> rawIdToEntry;

	@Shadow public abstract int getRawId(@Nullable T value);

	@Shadow @Final private Reference2IntMap<T> entryToRawId;
	@Shadow @Final private Map<RegistryKey<T>, RegistryEntryInfo> keyToEntryInfo;

	@Shadow public abstract Optional<RegistryEntry.Reference<T>> getEntry(Identifier id);

	@Shadow public abstract RegistryEntry<T> getEntry(T value);

	@Shadow public abstract Optional<RegistryEntry.Reference<T>> getEntry(int rawId);

	@Shadow
	SimpleRegistry.TagLookup<T> tagLookup;

	@Shadow protected abstract RegistryEntryList.Named<T> createNamedEntryList(TagKey<T> tag);

	@Shadow @Final private Map<TagKey<T>, RegistryEntryList.Named<T>> tags;

	@Shadow public abstract Optional<RegistryEntryList.Named<T>> getOptional(TagKey<T> tag);

	@Shadow @Final private RegistryKey<? extends Registry<T>> key;
	@Unique
	private boolean wasIntrusive = false;

	@Unique
	private final Multimap<TagKey<T>, RegistryKey<T>> prevTags = HashMultimap.create();

	@Inject(
		method = "<init>(Lnet/minecraft/registry/RegistryKey;Lcom/mojang/serialization/Lifecycle;Z)V",
		at = @At("RETURN")
	)
	public void init(RegistryKey<? extends Registry<T>> key, Lifecycle lifecycle, boolean intrusive, CallbackInfo ci) {
		if (intrusive) {
			wasIntrusive = true;
		}
	}

	@Override
	public boolean simpleCustomFeatures$unfreezeRegistry() {
		if (this.frozen) {
			this.frozen = false;
			if (wasIntrusive) {
				this.intrusiveValueToEntry = new IdentityHashMap<>();
			}
			tagLookup.forEach((key, entries) -> {
				for (var entry : entries) {
					entry.getKey().ifPresent(k -> {
						prevTags.put(key, k);
					});
				}
			});
			this.tagLookup = SimpleRegistry.TagLookup.ofUnbound();
			return true;
		}
		return false;
	}

	@ModifyArg(
		method = "freeze",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/registry/SimpleRegistry$TagLookup;fromMap(Ljava/util/Map;)Lnet/minecraft/registry/SimpleRegistry$TagLookup;"
		)
	)
	public Map<TagKey<T>, RegistryEntryList.Named<T>> fixTagsOnReFreeze(Map<TagKey<T>, RegistryEntryList.Named<T>> map) {
		if (!prevTags.isEmpty()) {
			ImmutableMap.Builder<TagKey<T>, RegistryEntryList.Named<T>> newMap = ImmutableMap.builder();
			for (var tagKey : prevTags.keySet()) {
				var list = tags.get(tagKey);
				if (list == null) {
					list = this.createNamedEntryList(tagKey);
				}

				((AccessorRegistryEntryListName<T>) list).setEntries(
						prevTags.get(tagKey).stream().map(
								key -> (RegistryEntry<T>) getOptional(key).orElse(null)
						).filter(Objects::nonNull).toList()
				);
				newMap.put(tagKey, list);
			}
			prevTags.clear();
			return newMap.build();
		}
		return map;
	}

	@Override
	public void simpleCustomFeatures$remove(T value) {
		getKey(value).ifPresent(key -> {
			this.keyToEntry.remove(key);
			this.idToEntry.remove(key.getValue());
			this.valueToEntry.remove(value);
			int id = getRawId(value);
			this.rawIdToEntry.remove(id);
			this.entryToRawId.remove(value, id);
			this.entryToRawId.reference2IntEntrySet().forEach(
					e -> {
						if (e.getIntValue() > id) {
							e.setValue(e.getIntValue()-1);
						}
					}
			);
			this.keyToEntryInfo.remove(key);
		});
	}

	@Override
	public void simpleCustomFeatures$removeIntrusiveEntry(T value) {
		if (this.intrusiveValueToEntry != null) {
			this.intrusiveValueToEntry.remove(value);
		}
	}

	@Override
	public boolean simpleCustomFeatures$isFrozen() {
		return frozen;
	}
}
