package nu.metacraft.simplecustomfeatures.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.simplecustomfeatures.RegistryExtensions;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> implements WritableRegistry<T>, RegistryExtensions<T> {

	@Shadow public abstract ResourceKey<? extends Registry<T>> key();

	@Shadow public abstract boolean containsKey(Identifier id);

	@Shadow private boolean frozen;
	@Shadow private @Nullable Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
	@Shadow @Final private Map<T, Holder.Reference<T>> byValue;

	@Shadow public abstract Optional<ResourceKey<T>> getResourceKey(T entry);

	@Shadow @Final private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
	@Shadow @Final private Map<Identifier, Holder.Reference<T>> byLocation;
	@Shadow @Final private ObjectList<Holder.Reference<T>> byId;

	@Shadow public abstract int getId(@Nullable T value);

	@Shadow @Final private Reference2IntMap<T> toId;
	@Shadow @Final private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

	@Shadow public abstract Optional<Holder.Reference<T>> get(Identifier id);

	@Shadow public abstract Holder<T> wrapAsHolder(T value);

	@Shadow public abstract Optional<Holder.Reference<T>> get(int rawId);

	@Shadow
	MappedRegistry.TagSet<T> allTags;

	@Shadow protected abstract HolderSet.Named<T> createTag(TagKey<T> tag);

	@Shadow @Final private Map<TagKey<T>, HolderSet.Named<T>> frozenTags;

	@Shadow public abstract Optional<HolderSet.Named<T>> get(TagKey<T> tag);

	@Shadow @Final private ResourceKey<? extends Registry<T>> key;
	@Unique
	private boolean wasIntrusive = false;

	@Unique
	private final Multimap<TagKey<T>, ResourceKey<T>> prevTags = HashMultimap.create();

	@Inject(
		method = "<init>(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;Z)V",
		at = @At("RETURN")
	)
	public void init(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, boolean intrusive, CallbackInfo ci) {
		if (intrusive) {
			wasIntrusive = true;
		}
	}

	@Override
	public boolean simpleCustomFeatures$unfreezeRegistry() {
		if (this.frozen) {
			this.frozen = false;
			if (wasIntrusive) {
				this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
			}
			allTags.forEach((key, entries) -> {
				for (var entry : entries) {
					entry.unwrapKey().ifPresent(k -> {
						prevTags.put(key, k);
					});
				}
			});
			this.allTags = MappedRegistry.TagSet.unbound();
			return true;
		}
		return false;
	}

	@ModifyArg(
		method = "freeze",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/core/MappedRegistry$TagSet;fromMap(Ljava/util/Map;)Lnet/minecraft/core/MappedRegistry$TagSet;"
		)
	)
	public Map<TagKey<T>, HolderSet.Named<T>> fixTagsOnReFreeze(Map<TagKey<T>, HolderSet.Named<T>> map) {
		if (!prevTags.isEmpty()) {
			ImmutableMap.Builder<TagKey<T>, HolderSet.Named<T>> newMap = ImmutableMap.builder();
			for (var tagKey : prevTags.keySet()) {
				var list = frozenTags.get(tagKey);
				if (list == null) {
					list = this.createTag(tagKey);
				}

				((HolderSetNamedAccessor<T>) list).setContents(
						prevTags.get(tagKey).stream().map(
								key -> (Holder<T>) get(key).orElse(null)
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
		getResourceKey(value).ifPresent(key -> {
			this.byKey.remove(key);
			this.byLocation.remove(key.identifier());
			this.byValue.remove(value);
			int id = getId(value);
			this.byId.remove(id);
			this.toId.remove(value, id);
			this.toId.reference2IntEntrySet().forEach(
					e -> {
						if (e.getIntValue() > id) {
							e.setValue(e.getIntValue()-1);
						}
					}
			);
			this.registrationInfos.remove(key);
		});
	}

	@Override
	public void simpleCustomFeatures$removeIntrusiveEntry(T value) {
		if (this.unregisteredIntrusiveHolders != null) {
			this.unregisteredIntrusiveHolders.remove(value);
		}
	}

	@Override
	public boolean simpleCustomFeatures$isFrozen() {
		return frozen;
	}
}
