package se.datasektionen.mc.metacraft_lib.time_getter;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

public class RegularTimeGetterRegistry {

	public static final Registry<RegularTimeGetterType<?>> REGISTRY = FabricRegistryBuilder.<RegularTimeGetterType<?>>createSimple(
			RegistryKey.ofRegistry(METAcraftLib.getID("regular_time_getter"))
	).buildAndRegister();

	public static final RegularTimeGetterType<Daily> DAILY = register("daily", new RegularTimeGetterType<>(
			Daily.CODEC
	));

	public static final RegularTimeGetterType<Weekly> WEEKLY = register("weekly", new RegularTimeGetterType<>(
			Weekly.CODEC
	));

	public static final RegularTimeGetterType<Or> OR = register("or", new RegularTimeGetterType<>(
			Or.CODEC
	));


	public static void init() {

	}

	private static <T extends RegularTimeGetter> RegularTimeGetterType<T> register(String id, RegularTimeGetterType<T> type) {
		return Registry.register(REGISTRY, METAcraftLib.getID(id), type);
	}

}
