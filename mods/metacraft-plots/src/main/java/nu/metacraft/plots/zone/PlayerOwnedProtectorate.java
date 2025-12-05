package nu.metacraft.plots.zone;

import com.google.common.collect.Lists;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.plots.METAcraftPlots;
import nu.metacraft.zones.zone.data.ZoneData;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlayerOwnedProtectorate extends ZoneData {

	private final Set<UUID> owners;
	private final Set<UUID> admins;
	private final Set<UUID> allowedPlayers;

	private long balance;

	private final List<IncrementItem> acceptedPaymentItems;
	private final List<DecrementItem<?>> acceptedDecrementItems;

	public static final MapCodec<PlayerOwnedProtectorate> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				UUIDUtil.LENIENT_CODEC.listOf().fieldOf("owners").forGetter(
						data -> data.owners.stream().toList()
				),
				UUIDUtil.LENIENT_CODEC.listOf().fieldOf("admins").forGetter(
						data -> data.admins.stream().toList()
				),
				UUIDUtil.LENIENT_CODEC.listOf().fieldOf("allowedPlayers").forGetter(
						data -> data.allowedPlayers.stream().toList()
				),
				Codec.LONG.fieldOf("balance").forGetter(data -> data.balance),
				IncrementItem.CODEC.listOf().fieldOf("acceptedPaymentItems").forGetter(
						data -> data.acceptedPaymentItems
				),
				DecrementItem.CODEC.listOf().fieldOf("acceptedDecrementItems").forGetter(
						data -> data.acceptedDecrementItems
				)
			).apply(instance, PlayerOwnedProtectorate::new)
	);

	public PlayerOwnedProtectorate() {
		this(
				new HashSet<>(), new HashSet<>(), new HashSet<>(), 1,
				Lists.newArrayList(
						new IncrementItem(
								ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, Items.DIAMOND_BLOCK).build(),
								1
						)
				),
				Lists.newArrayList(new DecrementItem(
						ItemPredicate.Builder.item().of(BuiltInRegistries.ITEM, Items.PLAYER_HEAD).build(),
						DataComponents.PROFILE, "", 1
				))
		);
	}

	public PlayerOwnedProtectorate(
			List<UUID> owners, List<UUID> admins, List<UUID> allowedPlayers, long balance,
			List<IncrementItem> acceptedPaymentItems, List<DecrementItem<?>> acceptedDecrementItems
	) {
		this(
				new HashSet<>(owners), new HashSet<>(admins), new HashSet<>(allowedPlayers),
				balance, acceptedPaymentItems, acceptedDecrementItems
		);
	}

	protected PlayerOwnedProtectorate(
			Set<UUID> owners, Set<UUID> admins, Set<UUID> allowedPlayers, long balance,
			List<IncrementItem> acceptedPaymentItems, List<DecrementItem<?>> acceptedDecrementItems
	) {
		this.owners = owners;
		this.admins = admins;
		this.allowedPlayers = allowedPlayers;
		this.balance = balance;
		this.acceptedPaymentItems = acceptedPaymentItems;
		this.acceptedDecrementItems = acceptedDecrementItems;
	}

	public boolean isOwner(UUID player) {
		return owners.contains(player);
	}

	public boolean isOwner(Player player) {
		return isOwner(player.getUUID());
	}

	public boolean isAdmin(Player player) {
		return isAdmin(player.getUUID());
	}

	public boolean isAdmin(UUID player) {
		return admins.contains(player);
	}

	public boolean canModifyMembers(UUID player) {
		return isAdmin(player) || isOwner(player);
	}

	public boolean canModifyMembers(Player player) {
		return canModifyMembers(player.getUUID());
	}

	public boolean isMember(Player player) {
		return isMember(player.getUUID());
	}

	public boolean isMember(UUID player) {
		return allowedPlayers.contains(player);
	}

	public boolean isAllowed(UUID player) {
		return canModifyMembers(player) || isMember(player) || balance <= 0;
	}

	public boolean isAllowed(Player player) {
		return isAllowed(player.getUUID());
	}

	public void addOwner(UUID player) {
		owners.add(player);
		markDirty();
	}

	public void removeOwner(UUID player) {
		owners.remove(player);
		markDirty();
	}

	public void addAdmin(UUID player) {
		admins.add(player);
		markDirty();
	}

	public void removeAdmin(UUID player) {
		admins.remove(player);
		markDirty();
	}

	public void addMember(UUID player) {
		allowedPlayers.add(player);
		markDirty();
	}

	public void removeMember(UUID player) {
		allowedPlayers.remove(player);
		markDirty();
	}

	public Set<UUID> getMembers() {
		return Collections.unmodifiableSet(allowedPlayers);
	}

	public Set<UUID> getOwners() {
		return Collections.unmodifiableSet(owners);
	}

	public Set<UUID> getAdmins() {
		return Collections.unmodifiableSet(admins);
	}

	public Stream<UUID> getOwnersAndAdmins() {
		return Stream.concat(getOwners().stream(), getAdmins().stream());
	}

	public Stream<UUID> getEveryone() {
		return Stream.concat(getOwnersAndAdmins(), getMembers().stream());
	}

	public Stream<UUID> getNonOwners() {
		return Stream.concat(getAdmins().stream(), getMembers().stream());
	}


	public long getBalance() {
		return balance;
	}

	public void applyDecrementItem(ItemStack stack) {
		if (stack.isEmpty()) return;
		for (var type : acceptedDecrementItems) {
			var owner = type.getOwner(stack, zone.getWorld().registryAccess()).orElse(null);
			if (owner != null && isAllowed(owner)) {
				long newAmount = balance - (long) type.amount * stack.getCount();
				if (newAmount < 0) {
					newAmount = 0;
				}
				stack.shrink((int) ((balance - newAmount) / type.amount));
				balance = newAmount;
				markDirty();
			}
		}
	}

	public boolean isValidIncrementItem(ItemStack stack) {
		for (var type : acceptedPaymentItems) {
			if (type.predicate.test(stack)) return true;
		}
		return false;
	}

	public void applyIncrementItem(ItemStack stack) {
		if (stack.isEmpty()) return;
		for (var type : acceptedPaymentItems) {
			if (type.predicate.test(stack)) {
				if (balance < 0) {
					balance = 0;
				}
				long newAmount = balance + (long) type.amount * stack.getCount();
				if (newAmount < 0) {
					newAmount = Long.MAX_VALUE;
				}
				stack.shrink((int) ((newAmount - balance) / type.amount));
				balance = newAmount;
				markDirty();
			}
		}
	}


	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return PlotDataTypes.PLAYER_PROTECTORATE;
	}

	public record IncrementItem(ItemPredicate predicate, int amount) {
		public static final Codec<IncrementItem> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ItemPredicate.CODEC.fieldOf("predicate").forGetter(IncrementItem::predicate),
						Codec.INT.fieldOf("amount").forGetter(IncrementItem::amount)
				).apply(instance, IncrementItem::new)
		);
	}

	public record DecrementItem<T>(ItemPredicate predicate, DataComponentType<T> uuidComponent, String uuidPath, int amount) {
		public static final Codec<DecrementItem<?>> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ItemPredicate.CODEC.fieldOf("predicate").forGetter(DecrementItem::predicate),
						BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().fieldOf("uuidComponent").forGetter(DecrementItem::uuidComponent),
						Codec.STRING.fieldOf("uuidPath").forGetter(DecrementItem::uuidPath),
						Codec.INT.fieldOf("amount").forGetter(DecrementItem::amount)
				).apply(instance, DecrementItem::new)
		);

		public Optional<UUID> getOwner(ItemStack stack, HolderLookup.Provider lookup) {
			if (!predicate.test(stack)) return Optional.empty();
			return Optional.ofNullable(stack.get(uuidComponent)).flatMap(component -> {
				DataResult<UUID> result = switch (component) {
					case CustomData nbt -> getDecoder(uuidPath).decode(NbtOps.INSTANCE, NbtOps.INSTANCE.getMap(nbt.copyTag()).getOrThrow());
					case CompoundTag nbt -> getDecoder(uuidPath).decode(NbtOps.INSTANCE, NbtOps.INSTANCE.getMap(nbt).getOrThrow());
					case ResolvableProfile profile -> DataResult.success(profile.partialProfile().id());
					default -> {
						var codec = uuidComponent.codec();
						if (codec == null) yield DataResult.error(() -> "Cannot fetch UUID from unserializable codec!");
						yield codec.encodeStart(lookup.createSerializationContext(JavaOps.INSTANCE), component).flatMap(
								encoded -> JavaOps.INSTANCE.getMap(encoded).flatMap(
										map -> getDecoder(uuidPath).decode(JavaOps.INSTANCE, map)
								)
						);
					}
				};
				return result.resultOrPartial(METAcraftPlots.LOGGER::error);
			});
		}

		private static MapDecoder<UUID> getDecoder(String uuidPath) {
			String[] path = uuidPath.split("\\.");
			return new MapDecoder.Implementation<>() {
				@Override
				public <T> Stream<T> keys(DynamicOps<T> ops) {
					return path.length > 0 ? Stream.of(ops.createString(path[0])) : Stream.empty();
				}

				private String getPathUntil(int pos) {
					return Arrays.stream(path).limit(pos).collect(Collectors.joining("."));
				}

				private <T> DataResult<T> decodeStep(MapLike<T> element, int index) {
					var result = element.get(path[index]);
					if (result != null) {
						return DataResult.success(result);
					} else {
						return DataResult.error(() -> "Element " + getPathUntil(index) + " did not exist");
					}
				}

				private <T> DataResult<T> addBetterError(DataResult<T> result, int index) {
					return result.mapError(msg -> "Error decoding " + getPathUntil(index) + ": " + msg);
				}

				@Override
				public <T> DataResult<UUID> decode(DynamicOps<T> ops, MapLike<T> input) {
					var current = DataResult.success(input);
					for (MutableInt i = new MutableInt(0); i.getValue() < path.length - 1; i.increment()) {
						current = current.flatMap(
								c -> decodeStep(c, i.getValue()).flatMap(
										next -> addBetterError(ops.getMap(next), i.getValue())
								)
						);
					}
					return current.flatMap(map -> {
						var lastKey = path[path.length - 1];
						var uuid = map.get(lastKey);
						if (uuid != null) {
							return addBetterError(UUIDUtil.AUTHLIB_CODEC.parse(ops, uuid), path.length);
						} else {
							var least = map.get(lastKey + "Least");
							var most = map.get(lastKey + "Most");
							if (least != null && most != null) {
								return addBetterError(ops.getNumberValue(least).flatMap(
										l -> ops.getNumberValue(most).map(m -> new UUID(m.longValue(), l.longValue()))
								), path.length);
							}
							return DataResult.error(() -> "No value at " + getPathUntil(path.length));
						}
					});
				}
			};
		}
	}

	@Override
	public Component toText(HolderLookup.Provider lookup) {
		return Component.literal(
				"PlayerOwnedProtectorate[owners=[" +
						owners.stream().map(
								owner -> zone.getWorld().getServer().services().nameToIdCache().get(owner)
										.map(NameAndId::name).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], admins=[" +
						admins.stream().map(
								owner -> zone.getWorld().getServer().services().nameToIdCache().get(owner)
										.map(NameAndId::name).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], members=[" +
						allowedPlayers.stream().map(
								owner -> zone.getWorld().getServer().services().nameToIdCache().get(owner)
										.map(NameAndId::name).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], balance=" +
						balance +
						", acceptedPaymentItems=[" +
						acceptedPaymentItems.stream().map(
								element -> IncrementItem.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), element).resultOrPartial(
										METAcraftPlots.LOGGER::error
								).map(Tag::toString).orElse(null)
						).collect(Collectors.joining(", ")) +
						", acceptedDecrementItems=[" +
						acceptedDecrementItems.stream().map(
								element -> DecrementItem.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), element).resultOrPartial(
										METAcraftPlots.LOGGER::error
								).map(Tag::toString).orElse(null)
						).collect(Collectors.joining(", ")) +
						"]"
		);
	}
}
