package nu.metacraft.moderation.exile;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.lib.util.ExtraCodecs;
import net.minecraft.world.World;
import nu.metacraft.moderation.METAcraftModeration;
import nu.metacraft.moderation.exile.rules.ZoneRule;
import nu.metacraft.moderation.exile.rules.ZoneRuleRegistry;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.RealZone;

import java.util.Map;
import java.util.stream.Collectors;

public class ExileDefinition {

	public static final String NAME = "name";

	protected final Multimap<RealZone, ZoneRule> zoneRules = HashMultimap.create();
	protected String commandOnExile = "tellraw @s {\"text\":\"Exiled\"}"; //TODO Save this!
	protected String commandOnPardon = "tellraw @s {\"text\":\"Pardoned\"}"; //TODO Save this!
	protected String name;
	private final MinecraftServer server;
	private Runnable markNeedsSaving;

	public void setSaveFunction(Runnable markNeedsSaving) {
		this.markNeedsSaving = markNeedsSaving;
	}

	public ExileDefinition(MinecraftServer server) {
		this.server = server;
	}

	public ExileDefinition(MinecraftServer server, String name) {
		this(server);
		this.name = name;
	}

	public void addRule(RealZone zone, ZoneRule rule) {
		zoneRules.put(zone, rule);
		markDirty();
	}

	public void removeRule(RealZone zone, ZoneRule rule) {
		zoneRules.remove(zone, rule);
		markDirty();
	}

	public void removeZone(RealZone zone) {
		zoneRules.removeAll(zone);
		markDirty();
	}

	public void setExileCommand(String command) {
		this.commandOnExile = command;
		markDirty();
	}

	public void setPardonCommand(String command) {
		this.commandOnPardon = command;
		markDirty();
	}

	public String getExileCommand() {
		return commandOnExile;
	}

	public String getPardonCommand() {
		return commandOnPardon;
	}

	public boolean ruleAppliesAt(RegistryKey<World> dim, BlockPos pos, ZoneRule rule) {
		return zoneRules.entries().stream().filter(
				entry -> entry.getKey().contains(dim, pos)
		).map(Map.Entry::getValue).collect(Collectors.toSet()).contains(rule);
	}

	public <T extends ServerPlayerEntity & ExilePlayerData> void onRemove(T player) {
		zoneRules.keySet().forEach(zone -> {
			if (!player.METAcraft_Moderation$getCurrentZones().contains(zone)) {
				zoneRules.get(zone).forEach(rule -> rule.enterAllowedArea(player));
			}
			player.METAcraft_Moderation$getCurrentZones().clear();
		});
	}


	public <T extends ServerPlayerEntity & ExilePlayerData> void tick(T player) {
		zoneRules.keySet().forEach(zone -> {
			if (!player.METAcraft_Moderation$getCurrentZones().contains(zone) && zone.contains(player.getWorld().getRegistryKey(), player.getBlockPos())) {
				zoneRules.get(zone).forEach(rule -> rule.enterAllowedArea(player));
				player.METAcraft_Moderation$getCurrentZones().add(zone);
			} else if (player.METAcraft_Moderation$getCurrentZones().contains(zone) && !zone.contains(player.getWorld().getRegistryKey(), player.getBlockPos())) {
				zoneRules.get(zone).forEach(rule -> rule.enterProhibitedArea(player));
				player.METAcraft_Moderation$getCurrentZones().remove(zone);
			}
			if (player.METAcraft_Moderation$getCurrentZones().contains(zone)) {
				zoneRules.get(zone).forEach(rule -> rule.tick(player));
			}
		});
	}

	public Serialized serialize() {
		return new Serialized(
				zoneRules.entries().stream().map(
						e -> Pair.of(
								e.getKey().getName(), e.getValue()
						)
				).collect(Multimaps.toMultimap(
						Pair::getFirst, Pair::getSecond, HashMultimap::create
				)), name
		);
	}

	public void deserialize(Serialized serialized) {
		this.name = serialized.name;
		for (var name : serialized.zoneRules.keySet()) {
			var zone = ZoneManager.getInstance(server).getZone(name);
			if (zone != null) {
				zoneRules.putAll(zone, serialized.zoneRules.get(name));
			} else {
				METAcraftModeration.LOGGER.fatal(
						"Removed zone " + name + " from ExileDefinition " + this.name + " because it did not exist!"
				);
			}
		}
	}

	public Text toText() {
		StringBuilder builder = new StringBuilder();
		builder.append("Name: ").append(name).append("\n");
		builder.append("ExileCommand: ").append(commandOnExile).append("\n");
		builder.append("PardonCommand: ").append(commandOnPardon).append("\n");
		builder.append("ZoneRules: \n");
		for (var zone : zoneRules.keySet()) {
			builder.append(" ").append(zone.getName()).append(": ");
			var it = zoneRules.get(zone).iterator();
			if (it.hasNext()) {
				builder.append(ZoneRuleRegistry.REGISTRY.getId(it.next()));
			}
			while (it.hasNext()) {
				builder.append(", ").append(ZoneRuleRegistry.REGISTRY.getId(it.next()));
			}
			builder.append("\n");
		}
		return Text.literal(builder.toString());
	}

	public void markDirty() {
		markNeedsSaving.run();
	}

	public String getName() {
		return name;
	}

	public record Serialized(
			Multimap<String, ZoneRule> zoneRules,
			String name
	) {
		public static final Codec<Serialized> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ExtraCodecs.unboundedMultimap(
								Codec.STRING, ZoneRuleRegistry.REGISTRY.getCodec(),
								HashMultimap::create
						).optionalFieldOf("zone_rules", HashMultimap.create()).forGetter(Serialized::zoneRules),
						Codec.STRING.fieldOf(NAME).forGetter(Serialized::name)
				).apply(instance, Serialized::new)
		);
	}

}
