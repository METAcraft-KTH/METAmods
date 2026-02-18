package nu.metacraft.zones.compat.leukocyte;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.RealZone;
import xyz.nucleoid.leukocyte.shape.ProtectionShape;
import xyz.nucleoid.stimuli.EventSource;
import xyz.nucleoid.stimuli.filter.EventFilter;

//FIXME Leukocyte!
/*public class ZoneShape implements ProtectionShape {

	protected String name;
	public static final MapCodec<ZoneShape> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(zone -> zone.name)
	).apply(instance, ZoneShape::new));

	//We need to know the current server, otherwise this won't work.
	//This will break with mods running multiple servers on different threads, but so will plenty of other mods.
	//If you really badly want to run multiple servers from one server
	//then split them up into different processes instead of threads.
	private static MinecraftServer server;

	static {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
		});
	}

	public ZoneShape(String name) {
		this.name = name;
	}

	@Override
	public EventFilter asEventFilter() {
		return new EventFilter() {
			@Override
			public boolean accepts(EventSource source) {
				ZoneManager manager = ZoneManager.getInstance(server);
				if (manager.containsZone(name)) {
					RealZone zone = manager.getZone(name);
					if (zone.getDim().equals(source.getDimension())) {
						if (source.getPos() == null) return true;
						return zone.isPosWithinZoneBoundsNoDimCheck(source.getPos());
					} else if (zone.hasRemoteZone(source.getDimension())) {
						if (source.getPos() == null) return true;
						return zone.getRemoteDimension(source.getDimension()).isPosWithinZoneBoundsNoDimCheck(source.getPos());
					}
				}
				return false;
			}
		};
	}

	@Override
	public MapCodec<? extends ProtectionShape> getCodec() {
		return CODEC;
	}

	@Override
	public MutableComponent displayItems() {
		return Component.literal("Zone: " + name);
	}

	@Override
	public MutableComponent displayShort() {
		return displayItems();
	}
}*/
