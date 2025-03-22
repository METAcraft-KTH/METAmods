package se.metacraft.discord_chat_fixer;

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
		System.out.println("Fetching API");
		if (MAIN != null) {
			try {
				System.out.println("Main exists");
				var config = MAIN.getField("CONFIG").get(null);
				if (config != null) {
					System.out.println("Config exists");
					var generic = config.getClass().getField("generic").get(config);
					if (generic != null) {
						System.out.println("Generic exists");
						var avatarAPI = generic.getClass().getField("avatarApi").get(generic);
						if (avatarAPI != null) {
							System.out.println("Avatar API exists " + avatarAPI);
							return (String) avatarAPI;
						}
					}
				}
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
		}
		return "";
	}

}
