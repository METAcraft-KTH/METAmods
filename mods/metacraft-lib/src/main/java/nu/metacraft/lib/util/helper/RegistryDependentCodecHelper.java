package nu.metacraft.lib.util.helper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.dialog.Dialog;

public class RegistryDependentCodecHelper {

	private static final String OBJECT_STORAGE = "ObjectStorage";

	private static final Set<Object> REGISTRY_DEPENDENT = new HashSet<>();
	private static final Set<Object> NOT_REGISTRY_DEPENDENT = new HashSet<>();

	public static void markRegistryDependent(Codec<?> codec) {
		REGISTRY_DEPENDENT.add(codec);
	}

	public static void markRegistryDependent(MapCodec<?> codec) {
		REGISTRY_DEPENDENT.add(codec);
	}

	public static void markNotRegistryDependent(Codec<?> codec) {
		NOT_REGISTRY_DEPENDENT.add(codec);
	}

	public static void markNotRegistryDependent(MapCodec<?> codec) {
		NOT_REGISTRY_DEPENDENT.add(codec);
	}

	private static boolean isObjectStorage(String name) {
		return name.startsWith(OBJECT_STORAGE);
	}

	private static boolean isRegistryDependent(String orgName) {
		String name = orgName;
		while (true) {
			int index = name.indexOf(OBJECT_STORAGE);
			if (index == -1) {
				break;
			}
			int depth = 0;
			for (int i = index + OBJECT_STORAGE.length(); i < name.length(); i++) {
				if (name.charAt(i) == '[') {
					depth++;
				}
				if (name.charAt(i) == ']') {
					depth--;
				}
				if (depth == 0) {
					name = name.substring(0, index) + name.substring(i+1);
					break;
				}
			}
		}
		return name.contains("RegistryFixedCodec") || name.contains("ContextRetrievalCodec");
	}

	private static boolean returnTrue(Object codec, Consumer<String> nameGetter) {
		nameGetter.accept(codec.toString() + " " + codec.getClass());
		REGISTRY_DEPENDENT.add(codec);
		return true;
	}

	private static boolean returnFalse(Object codec) {
		NOT_REGISTRY_DEPENDENT.add(codec);
		return false;
	}

	private static final ThreadLocal<Set<Object>> CURRENT_STACK = ThreadLocal.withInitial(HashSet::new);

	private static boolean isGeneralRegistryDependent(Object codec, Consumer<String> nameGetter) {
		if (codec == null) return false;
		if (NOT_REGISTRY_DEPENDENT.contains(codec)) return false;
		if (REGISTRY_DEPENDENT.contains(codec)) return true;
		if (CURRENT_STACK.get().contains(codec)) return false;
		if (codec instanceof RegistryFixedCodec<?>) return returnTrue(codec, nameGetter);
		if (isObjectStorage(codec.toString())) return false;
		if (isRegistryDependent(codec.toString())) return returnTrue(codec, nameGetter);
		CURRENT_STACK.get().add(codec);
		try {
			for (var field : codec.getClass().getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) continue;
				try {
					field.setAccessible(true);
					try {
						var o = field.get(codec);
						if (isGeneralRegistryDependent(o, nameGetter)) return returnTrue(codec, nameGetter);
					} catch (IllegalAccessException ignored) {}

					if (Supplier.class.isAssignableFrom(field.getType())) {
						try {
							Supplier<?> c = (Supplier<?>) field.get(codec);
							Object result = c.get();
							if (isGeneralRegistryDependent(result, nameGetter)) return returnTrue(codec, nameGetter);
						} catch (Throwable ignored) {} //Apparently there's a static supplier somewhere that throws IllegalStateException when run.
					}

					if (List.class.isAssignableFrom(field.getType())) {
						try {
							List<?> c = (List<?>) field.get(codec);
							for (var o : c) {
								if (isGeneralRegistryDependent(o, nameGetter)) return returnTrue(codec, nameGetter);
							}
						} catch (IllegalAccessException ignored) {}
					}
				} catch (InaccessibleObjectException ignored) {}
			}
			return returnFalse(codec);
		} finally {
			CURRENT_STACK.get().remove(codec);
		}
	}

	public static boolean isRegistryDependent(MapCodec<?> codec, Consumer<String> nameGetter) {
		return isGeneralRegistryDependent(codec, nameGetter);
	}

	public static boolean isRegistryDependent(Codec<?> codec, Consumer<String> nameGetter) {
		return isGeneralRegistryDependent(codec, nameGetter);
	}

	static {
		/**
		 * @see {@link net.minecraft.component.type.ItemEnchantmentsComponent}
		 * @see {@link net.minecraft.component.type.BlockPredicatesComponent}
		 */
		markRegistryDependent(DataComponentPatch.CODEC);
		markRegistryDependent(DataComponentType.VALUE_MAP_CODEC);

		//A consequence of above.
		markRegistryDependent(HoverEvent.CODEC);
		markRegistryDependent(Dialog.DIRECT_CODEC);
	}
}
