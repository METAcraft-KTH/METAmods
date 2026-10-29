package se.metacraft.info_commands.command_action;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import se.metacraft.info_commands.Info;

public class CommandActionRegistry {

	public static final Registry<MapCodec<? extends CommandAction>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends CommandAction>>create(
		ResourceKey.createRegistryKey(Info.getId("command_action"))
	).buildAndRegister();

	protected static final ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends CommandAction>> ID_MAPPER = new ExtraCodecs.LateBoundIdMapper<>();

	static {
		RegistryEntryAddedCallback.event(REGISTRY).register((rawId, id, object) -> {
			ID_MAPPER.put(id.toString(), object);
		});
	}

	public static void init() {
		register("message", MessageCommandAction.CODEC);
		register("command", CommandCommandAction.CODEC);
	}

	private static void register(String id, MapCodec<? extends CommandAction> codec) {
		Registry.register(REGISTRY, Info.getId(id), codec);
	}

}
