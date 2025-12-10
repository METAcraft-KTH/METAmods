import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestInit implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

	private static boolean started = false;

	private static void beforeAll() {
		TestHelper.init(
				() -> {
					TestPlayerData.init();
				},
				METAcraftLib::new
		);
	}

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (!started) {
			started = true;
			beforeAll();
			context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("metacraft-lib", this);
		}
	}

	@Override
	public void close() throws Throwable {

	}
}
