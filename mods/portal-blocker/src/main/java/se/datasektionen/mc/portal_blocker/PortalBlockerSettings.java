package se.datasektionen.mc.portal_blocker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import se.datasektionen.mc.portal_blocker.portal_type.PortalType;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;
import se.datasektionen.mc.portal_blocker.zone.PortalZoneData;
import se.datasektionen.mc.zones.ZoneManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PortalBlockerSettings extends PersistentState {

	private static final String stateKey = "portal-blocker";
	private static final String globalPortals = "global-portal-states";

	public static PortalBlockerSettings getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), stateKey);
	}

	private static PersistentState.Type<PortalBlockerSettings> getType(MinecraftServer server) {
		return new Type<>(
				() -> createNew(server), (nbt, lookup) -> fromNbt(server, nbt, lookup), null
		);
	}

	private static PortalBlockerSettings createNew(MinecraftServer server) {
		PortalBlocker.LOGGER.info("No previous state found, setting default values");
		return new PortalBlockerSettings(server);
	}

	private static PortalBlockerSettings fromNbt(MinecraftServer server, NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		PortalBlocker.LOGGER.info("Previous state found, loading values");
		PortalBlockerSettings settings = new PortalBlockerSettings(server);
		settings.readNbt(tag, lookup);
		return settings;
	}

	protected final MinecraftServer server;

	protected PortalBlockerSettings(MinecraftServer server) {
		this.server = server;
	}

	protected Map<PortalType, PortalState> portalIsBlockedMap = new HashMap<>();

	protected <T> Optional<T> get(RegistryKey<World> dim, BlockPos pos, Function<PortalZoneData, Optional<T>> mapper) {
		return ZoneManager.getInstance(server).getValueForPrimaryZone(
				dim, pos, zone -> mapper.apply(zone.getOrCreate(PortalBlocker.DATA))
		);
	}

	public boolean isPortalBlocked(
			PortalType type, RegistryKey<World> dim, PortalState.BlockingType blockingType, BlockPos pos
	) {
		return get(dim, pos, data -> {
			var value = data.getBlockedState(type, blockingType);
			if (value != PortalZoneData.BlockResult.DEFAULT) {
				return Optional.of(value == PortalZoneData.BlockResult.BLOCKED);
			}
			return Optional.empty();
		}).orElse(isPortalBlockedGlobally(type, blockingType));
	}

	public boolean isPortalBlocked(
			PortalType type, RegistryKey<World> dim, PortalState.BlockingType blockingType, Iterable<BlockPos> positions
	) {
		return ZoneManager.getInstance(server).getValueForPrimaryZone(
				dim, zone -> {
					boolean shouldAllow = true;
					for (BlockPos pos : positions) {
						if (!zone.contains(pos)) return Optional.empty();
						var state = zone.get(PortalBlocker.DATA).map(
								data -> data.getBlockedState(type, blockingType)
						).orElse(PortalZoneData.BlockResult.DEFAULT);
						if (state == PortalZoneData.BlockResult.BLOCKED) {
							return Optional.of(true);
						}
						if (state != PortalZoneData.BlockResult.ALLOWED) {
							shouldAllow = false;
						}
					}
					return shouldAllow ? Optional.of(false) : Optional.empty();
				}
		).orElse(isPortalBlockedGlobally(type, blockingType));
	}

	private PortalState get(PortalType type) {
		return portalIsBlockedMap.computeIfAbsent(type, t -> new PortalState(
				PortalState.BlockingType.CREATION, PortalState.BlockingType.TRAVEL
		));
	}

	public boolean isPortalBlockedGlobally(PortalType type, PortalState.BlockingType blockingType) {
		return get(type).isBlocked(blockingType);
	}

	public void setPortalBlockedGlobally(PortalType type, PortalState.BlockingType state, boolean blocked) {
		get(type).setBlocked(state, blocked);
		type.onGlobalStateChange(server, blocked, state);
		markDirty();
	}

	public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		portalIsBlockedMap.clear();
		NbtCompound globalPortalMap = tag.getCompound(globalPortals);
		for (String key : globalPortalMap.getKeys()) {
			PortalType type = PortalTypeRegistry.REGISTRY.get(Identifier.tryParse(key));
			if (type != null) {
				portalIsBlockedMap.put(type, new PortalState().fromNBT(globalPortalMap.get(key)));
			} else {
				PortalBlocker.LOGGER.error(key + " is not a valid portal type!");
				markDirty();
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtCompound portalIsBlockedNBT = new NbtCompound();
		for (Map.Entry<PortalType, PortalState> state : portalIsBlockedMap.entrySet()) {
			portalIsBlockedNBT.put(state.getKey().toString(), state.getValue().toNBT());
		}
		tag.put(globalPortals, portalIsBlockedNBT);
		return tag;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("EndDisableSettings [");
		for (Map.Entry<PortalType, PortalState> state : portalIsBlockedMap.entrySet()) {
			builder.append(state.getKey().toString()).append("=").append(state.getValue()).append(",");
		}
		builder.replace(builder.length()-1, builder.length(), "]");
		return builder.toString();
	}

}
