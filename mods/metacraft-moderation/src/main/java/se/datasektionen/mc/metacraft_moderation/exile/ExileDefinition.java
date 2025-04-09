package se.datasektionen.mc.metacraft_moderation.exile;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_moderation.METAcraftModeration;
import se.datasektionen.mc.metacraft_moderation.exile.rules.ZoneRule;
import se.datasektionen.mc.metacraft_moderation.exile.rules.ZoneRuleRegistry;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.zone.RealZone;

import java.util.Map;
import java.util.stream.Collectors;

public class ExileDefinition {

	public static final String NAME = "name";
	public static final String ZONE_RULES = "zone_rules";

	protected final Multimap<RealZone, ZoneRule> zoneRules = HashMultimap.create();
	protected String commandOnExile = "tellraw @s {\"text\":\"Exiled\"}";
	protected String commandOnPardon = "tellraw @s {\"text\":\"Pardoned\"}";
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

	public NbtCompound toNBT() {
		NbtCompound nbt = new NbtCompound();
		NbtCompound rules = new NbtCompound();
		zoneRules.keySet().forEach(zone -> {
			NbtList list = new NbtList();
			zoneRules.get(zone).forEach(rule -> {
				list.add(NbtString.of(rule.getID().toString()));
			});
			rules.put(zone.getName(), list);
		});
		nbt.put(ZONE_RULES, rules);
		nbt.putString(NAME, name);
		return nbt;
	}

	public void fromNBT(NbtCompound nbt) {
		name = nbt.getString(NAME);
		NbtCompound ruleMap = nbt.getCompound(ZONE_RULES);
		zoneRules.clear();
		for (String name : ruleMap.getKeys()) {
			var zone = ZoneManager.getInstance(server).getZone(name);
			if (zone != null) {
				var rules = ruleMap.getList(name, NbtElement.STRING_TYPE);
				for (NbtElement rule : rules) {
					var actualRule = ZoneRuleRegistry.REGISTRY.get(Identifier.tryParse(rule.asString()));
					if (actualRule != null) {
						zoneRules.put(zone, actualRule);
					} else {
						METAcraftModeration.LOGGER.fatal(
								"Removed rule " + rule.asString() + " from zone " + name +
								" in ExileDefinition " + this.name + " because it did not exist!"
						);
					}
				}
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

}
