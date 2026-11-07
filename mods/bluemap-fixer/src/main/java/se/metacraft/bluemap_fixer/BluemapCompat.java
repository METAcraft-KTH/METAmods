package se.metacraft.bluemap_fixer;

import de.bluecolored.bluemap.api.BlueMapAPI;

public class BluemapCompat {


	public static void init() {
		BlueMapAPI.onEnable(bluemap -> {
			var prev = bluemap.getPlugin().getPlayerDisplayNameProvider();
			bluemap.getPlugin().setPlayerDisplayNameProvider(id -> {
				var name = prev.get(id);
				if (name != null && name.isEmpty()) {
					return " ";
				}
				return name;
			});
		});
	}

}
