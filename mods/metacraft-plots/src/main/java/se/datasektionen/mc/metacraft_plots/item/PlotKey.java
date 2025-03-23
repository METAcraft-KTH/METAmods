package se.datasektionen.mc.metacraft_plots.item;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_plots.zone.PlotDataTypes;
import se.datasektionen.mc.metacraft_plots.zone.PlotData;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.zone.Zone;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class PlotKey extends Item implements PolymerItem {

	private final BiFunction<ItemStack, MinecraftServer, Identifier> modelIdGetter;

	public PlotKey(Item.Settings settings, BiFunction<ItemStack, MinecraftServer, Identifier> modelIdGetter) {
		super(settings);
		this.modelIdGetter = modelIdGetter;
	}

	public static Optional<ZoneContainer> getZone(ItemStack stack, MinecraftServer server) {
		return getZone(stack, server, true);
	}

	public static void addKeyToRevoke(ItemStack stack, String prevFriendlyName, String prevZone) {
		stack.set(
				PlotComponents.KEYS_TO_REVOKE,
				stack.getOrDefault(PlotComponents.KEYS_TO_REVOKE, new KeysToRevoke(List.of())).with(
						new KeysToRevoke.ZoneEntry(prevFriendlyName, prevZone)
				)
		);
	}

	private static Optional<ZoneContainer> getZone(ItemStack stack, MinecraftServer server, boolean requireSecret) {
		if (!(stack.getItem() instanceof PlotKey)) {
			return Optional.empty();
		}
		return Optional.ofNullable(stack.get(PlotComponents.KEY)).flatMap(
				key -> {
					var name = key.zoneName;
					var secret = key.zoneSecret;
					var zone = ZoneManager.getInstance(server).getZone(name);
					return Optional.ofNullable(zone).flatMap(
							z -> z.get(PlotDataTypes.PLOT).flatMap(data -> {
								if (!requireSecret || data.checkSecret(secret)) {
									return Optional.of(new ZoneContainer(z, data));
								} else {
									return Optional.empty();
								}
							})
					);
				}
		);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		super.inventoryTick(stack, world, entity, slot);
		if (
			!world.isClient() && entity instanceof ServerPlayerEntity
		) {
			if (
					stack.getCount() == 1 &&
					stack.contains(PlotComponents.CREATION_SECRET)
			) {
				getZone(stack, world.getServer(), false).ifPresent(zone -> {
					turnIntoSecondaryKeyIfCreationSecret(stack, zone.plotData);
				});
			}
			if (stack.contains(PlotComponents.KEYS_TO_REVOKE)) {
				var prevZones = stack.get(PlotComponents.KEYS_TO_REVOKE).zones;
				for (var z : prevZones) {
					var name = z.zone;
					var friendlyName = z.friendlyName;
					Optional.ofNullable(ZoneManager.getInstance(world.getServer()).getZone(name)).flatMap(
							zone -> zone.get(PlotDataTypes.PLOT)
					).ifPresent(data -> {
						data.revokeSecondarySecret(friendlyName);
					});
				}
				stack.remove(PlotComponents.KEYS_TO_REVOKE);
			}
		}
	}

	public static Optional<ItemStack> createKey(String friendlyName, PlotData data) {
		return data.addSecondarySecretWithFriendlyName(friendlyName).map(secret -> {
			var key = new ItemStack(PlotItems.PLOT_KEY);
			turnIntoKey(key, data.getZone(), secret);
			key.set(PlotComponents.FRIENDLY_NAME, new FriendlyNameComponent(friendlyName));
			return key;
		});
	}

	public static ItemStack createUninitialisedKeyFromMasterKey(ItemStack masterKey) {
		var key = new ItemStack(PlotItems.PLOT_KEY);
		Optional.ofNullable(masterKey.get(PlotComponents.KEY)).ifPresent(data -> {
			key.set(PlotComponents.KEY, new KeyComponent(data.zoneName, ""));
			key.set(PlotComponents.CREATION_SECRET, new CreationSecret(data.zoneSecret));
		});
		return key;
	}

	public static ItemStack createKey(PlotData data) {
		var key = new ItemStack(PlotItems.PLOT_KEY);
		turnIntoSecondaryKeyFor(key, data);
		return key;
	}

	public static void setPlaceholderName(ItemStack stack, String name) {
		stack.set(PlotComponents.PLACEHOLDER_NAME, new PlaceholderName(name));
	}

	public static String getPlot(ItemStack stack) {
		return Optional.ofNullable(stack.get(PlotComponents.KEY)).map(key -> key.zoneName).orElse(null);
	}
	public static String getFriendlyName(ItemStack stack) {
		return Optional.ofNullable(stack.get(PlotComponents.FRIENDLY_NAME)).map(key -> key.friendlyName).orElse(null);
	}

	public static ItemStack createMasterKey(PlotData data) {
		var key = new ItemStack(PlotItems.PLOT_MASTER_KEY);
		turnIntoKey(key, data.getZone(), data.getPlotSecret());
		return key;
	}

	private static void turnIntoKey(ItemStack key, Zone zone, String secret) {
		key.set(PlotComponents.KEY, new KeyComponent(zone.getName(), secret));
		removePlaceholderName(key);
	}

	private static void turnIntoSecondaryKeyFor(ItemStack key, PlotData data) {
		var secondarySecret = data.addSecondarySecret();
		turnIntoKey(key, data.getZone(), secondarySecret.secret());
		key.set(PlotComponents.FRIENDLY_NAME, new FriendlyNameComponent(secondarySecret.friendlyName()));
		key.remove(PlotComponents.CREATION_SECRET);
	}

	private static void turnIntoSecondaryKeyIfCreationSecret(ItemStack key, PlotData data) {
		Optional.ofNullable(key.get(PlotComponents.CREATION_SECRET)).filter(
				k -> k.creationSecret.equals(data.getPlotSecret())
		).ifPresentOrElse(nbt -> {
			turnIntoSecondaryKeyFor(key, data);
		}, () -> {
			key.applyComponentsFrom(key.getDefaultComponents());
		});
	}

	public static void revokePlotKey(ItemStack plotKey, MinecraftServer server) {
		PlotKey.getZone(plotKey, server).ifPresent(zone -> {
			Optional.ofNullable(PlotKey.getFriendlyName(plotKey)).ifPresent(name -> {
				zone.plotData().revokeSecondarySecret(name);
			});
		});
	}

	public static void renameKey(ItemStack plotKey, MinecraftServer server, String newName) {
		PlotKey.getZone(plotKey, server).flatMap(
				zone -> zone.plotData().addSecondarySecretWithFriendlyName(plotKey.getName().getString()).flatMap(
						secret -> Optional.of(Pair.of(zone, secret))
				)
		).ifPresent(pair -> {
			PlotKey.revokePlotKey(plotKey, server);
			turnIntoKey(plotKey, pair.first().zone(), pair.second());
			plotKey.set(PlotComponents.FRIENDLY_NAME, new FriendlyNameComponent(newName));
		});
		PlotKey.removePlaceholderName(plotKey);
	}

	public static void removePlaceholderName(ItemStack plotKey) {
		plotKey.remove(PlotComponents.PLACEHOLDER_NAME);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent component, Consumer<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, component, tooltip, type);
		if (stack.contains(PlotComponents.KEY)) {
			var zoneName = stack.get(PlotComponents.KEY).zoneName;
			if (stack.contains(PlotComponents.PLACEHOLDER_NAME) && component.shouldDisplay(PlotComponents.PLACEHOLDER_NAME)) {
				tooltip.accept(Text.literal(zoneName + ":" + stack.get(PlotComponents.PLACEHOLDER_NAME).placeholderName));
			} else if (stack.contains(PlotComponents.FRIENDLY_NAME) && component.shouldDisplay(PlotComponents.FRIENDLY_NAME)) {
				tooltip.accept(Text.literal(zoneName + ":" + stack.get(PlotComponents.FRIENDLY_NAME).friendlyName));
			} else if (component.shouldDisplay(PlotComponents.KEY)) {
				tooltip.accept(Text.literal(zoneName));
			}
		}
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext ctx) {
		return modelIdGetter.apply(itemStack, Optional.ofNullable(ctx.getPlayer()).map(ServerPlayerEntity::getServer).orElse(null));
	}

	public record ZoneContainer(Zone zone, PlotData plotData) {}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
		return Items.FLINT;
	}

	@Override
	public void onItemEntityDestroyed(ItemEntity entity) {
		super.onItemEntityDestroyed(entity);
		if (this == PlotItems.PLOT_KEY) {
			PlotKey.revokePlotKey(entity.getStack(), entity.getServer());
		}
	}

	public record KeyComponent(
			String zoneName, String zoneSecret
	) {
		public static final Codec<KeyComponent> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.fieldOf("zone_name").forGetter(KeyComponent::zoneName),
						Codec.STRING.fieldOf("zone_secret").forGetter(KeyComponent::zoneSecret)
				).apply(instance, KeyComponent::new)
		);
	}

	public record FriendlyNameComponent(
			String friendlyName
	) {
		public static final Codec<FriendlyNameComponent> CODEC = Codec.STRING.xmap(FriendlyNameComponent::new, FriendlyNameComponent::friendlyName);
	}

	public record PlaceholderName(
			String placeholderName
	) {
		public static final Codec<PlaceholderName> CODEC = Codec.STRING.xmap(PlaceholderName::new, PlaceholderName::placeholderName);
	}

	public record CreationSecret(
			String creationSecret
	) {
		public static final Codec<CreationSecret> CODEC = Codec.STRING.xmap(CreationSecret::new, CreationSecret::creationSecret);
	}

	public record KeysToRevoke(
			List<ZoneEntry> zones
	) {

		public record ZoneEntry(String friendlyName, String zone) {
			public static final Codec<ZoneEntry> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							Codec.STRING.fieldOf("friendly_name").forGetter(ZoneEntry::friendlyName),
							Codec.STRING.fieldOf("secret").forGetter(ZoneEntry::zone)
					).apply(instance, ZoneEntry::new)
			);
		}

		public KeysToRevoke with(ZoneEntry entry) {
			return new KeysToRevoke(ImmutableList.<ZoneEntry>builder().addAll(zones).add(entry).build());
		}

		public static final Codec<KeysToRevoke> CODEC = ZoneEntry.CODEC.listOf().xmap(KeysToRevoke::new, KeysToRevoke::zones);
	}
}
