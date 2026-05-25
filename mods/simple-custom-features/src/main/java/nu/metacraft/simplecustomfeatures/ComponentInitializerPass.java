package nu.metacraft.simplecustomfeatures;

public class ComponentInitializerPass {

	private static final ThreadLocal<Object> PASSED = ThreadLocal.withInitial(() -> null);

	public static void pass(Object object) {
		PASSED.set(object);
	}

	public static Object catchIt() {
		var result = PASSED.get();
		PASSED.remove();
		return result;
	}

}
