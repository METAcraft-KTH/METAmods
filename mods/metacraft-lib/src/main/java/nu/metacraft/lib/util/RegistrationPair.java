package nu.metacraft.lib.util;

import net.minecraft.resources.ResourceKey;

public record RegistrationPair<T>(ResourceKey<T> key, T value) {
}
