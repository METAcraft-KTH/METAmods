package nu.metacraft.discord_chat_fixer;

public class DiscordConfigAccessor {

	private static final Class<?> MAIN;

	static {
		Class<?> main;
		try {
			main = Class.forName("com.xujiayao.discord_mc_chat.Main");
		} catch (ClassNotFoundException ignored) {
			main = null;
		}
		MAIN = main;
	}

	public static String getAvatarAPI() {
		if (MAIN != null) {
			try {
				var config = MAIN.getField("CONFIG").get(null);
				if (config != null) {
					var generic = config.getClass().getField("generic").get(config);
					if (generic != null) {
						var avatarAPI = generic.getClass().getField("avatarApi").get(generic);
						if (avatarAPI != null) {
							return (String) avatarAPI;
						}
					}
				}
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
		}
		return "";
	}

}
