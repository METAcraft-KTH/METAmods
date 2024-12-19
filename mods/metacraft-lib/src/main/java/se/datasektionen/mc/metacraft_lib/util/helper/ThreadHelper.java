package se.datasektionen.mc.metacraft_lib.util.helper;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ThreadHelper {

	public static <T> void updateAtomic(AtomicReference<T> atomic, Supplier<? extends T> newValue) {
		while (true) {
			var prev = atomic.get();
			var next = newValue.get();
			if (atomic.compareAndSet(prev, next)) {
				break;
			}
		}
	}

}
