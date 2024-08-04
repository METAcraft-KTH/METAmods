package se.datasektionen.mc.simplecustomfeatures.mixin;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.simplecustomfeatures.RegistryExtensions;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

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

	@Unique
	private boolean wasIntrusive = false;

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
			return true;
		}
		return false;
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

}
