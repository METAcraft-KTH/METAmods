package nu.metacraft.simplecustomfeatures.compat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.block.Portal;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PortalBlockerCompat {

	public static final ObjectType<BlockPortalType, PortalType> BLOCK_PORTAL_TYPE = ObjectRegistry.register(
			PortalBlocker.getID("block_portal_type"), BlockPortalType.CODEC, PortalTypeRegistry.REGISTRY
	);

	public static List<Registry<?>> addPortalTypeRegistry(List<Registry<?>> registries) {
		try {
			registries.add(PortalTypeRegistry.REGISTRY);
		} catch (Throwable ignored) {
			registries = new ArrayList<>(registries);
			registries.add(PortalTypeRegistry.REGISTRY);
		}
		return registries;
	}

	public static <T extends Block & Portal> Multimap<Identifier, BaseObject<?>> addPortalType(
			Multimap<Identifier, BaseObject<?>> map, Identifier id, T portal,
			PortalTypeData data
	) {
		var object = new BlockPortalType(Set.of(portal), data);
		try {
			map.put(id, object);
		} catch (Throwable ignored) {
			map = ArrayListMultimap.create(map);
			map.put(id, object);
		}
		return map;
	}

	public static boolean isActivationBlocked(Portal portal, MinecraftServer server, RegistryKey<World> dim, BlockPos pos) {
		PortalType type = BlockPortalType.getFromPortal(portal);
		if (type == null) return false;
		return PortalBlockerSettings.getInstance(server).isPortalBlocked(
				type, dim, PortalState.BlockingType.ACTIVATION, pos
		);
	}

	public static boolean isActivationBlocked(Portal portal, MinecraftServer server, RegistryKey<World> dim, Iterable<BlockPos> positions) {
		PortalType type = BlockPortalType.getFromPortal(portal);
		if (type == null) return false;
		return PortalBlockerSettings.getInstance(server).isPortalBlocked(
				type, dim, PortalState.BlockingType.ACTIVATION, positions
		);
	}

	public static boolean isGenerationBlocked(Portal portal, MinecraftServer server, RegistryKey<World> dim, Iterable<BlockPos> positions) {
		PortalType type = BlockPortalType.getFromPortal(portal);
		if (type == null) return false;
		return PortalBlockerSettings.getInstance(server).isPortalBlocked(
				type, dim, PortalState.BlockingType.GENERATION, positions
		);
	}

	public static void init() {

	}

}
