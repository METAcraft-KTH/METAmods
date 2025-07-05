package nu.metacraft.portal_blocker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.portal_blocker.zone.PortalZoneData;
import nu.metacraft.portal_blocker.zone.ZoneDataPortalBlocker;
import nu.metacraft.zones.ZoneManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PortalBlockerSettings extends PersistentState {

	private static final Codec<Map<PortalType, PortalState>> PORTAL_MAP = Codec.unboundedMap(
			PortalTypeRegistry.REGISTRY.getCodec(), PortalState.CODEC
	);

	public static PortalBlockerSettings getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	private static final PersistentStateType<PortalBlockerSettings> TYPE = new PersistentStateType<>(
			"portal-blocker", ctx -> createNew(ctx.getWorldOrThrow().getServer()),
			ctx -> createCodec(ctx.getWorldOrThrow().getServer()), null
	);

	private static PortalBlockerSettings createNew(MinecraftServer server) {
		PortalBlocker.LOGGER.info("No previous state found, setting default values");
		return new PortalBlockerSettings(server);
	}

	private static Codec<PortalBlockerSettings> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						PORTAL_MAP.fieldOf("global-portal-states").forGetter(s -> s.portalIsBlockedMap)
				).apply(instance, data -> fromData(server, data))
		);
	}

	private static PortalBlockerSettings fromData(MinecraftServer server, Map<PortalType, PortalState> data) {
		PortalBlocker.LOGGER.info("Previous state found, loading values");
		PortalBlockerSettings settings = new PortalBlockerSettings(server);
		settings.portalIsBlockedMap.putAll(data);
		return settings;
	}

	protected final MinecraftServer server;

	protected PortalBlockerSettings(MinecraftServer server) {
		this.server = server;
	}

	protected Map<PortalType, PortalState> portalIsBlockedMap = new HashMap<>();

	protected <T> Optional<T> get(RegistryKey<World> dim, BlockPos pos, Function<PortalZoneData, Optional<T>> mapper) {
		return ZoneManager.getInstance(server).getValueForPrimaryZone(
				dim, pos, zone -> mapper.apply(zone.getOrCreate(ZoneDataPortalBlocker.PORTAL_DATA))
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
						if (!zone.isPosWithinZoneBoundsNoDimCheck(pos)) return Optional.empty();
						var state = zone.get(ZoneDataPortalBlocker.PORTAL_DATA).map(
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
