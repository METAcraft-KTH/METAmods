package se.datasektionen.mc.metacraft_plots.zone;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import org.apache.commons.lang3.mutable.MutableInt;
import se.datasektionen.mc.metacraft_plots.METAcraftPlots;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

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
				Uuids.STRICT_CODEC.listOf().fieldOf("owners").forGetter(
						data -> data.owners.stream().toList()
				),
				Uuids.STRICT_CODEC.listOf().fieldOf("admins").forGetter(
						data -> data.admins.stream().toList()
				),
				Uuids.STRICT_CODEC.listOf().fieldOf("allowedPlayers").forGetter(
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
								ItemPredicate.Builder.create().items(Registries.ITEM, Items.DIAMOND_BLOCK).build(),
								1
						)
				),
				Lists.newArrayList(new DecrementItem(
						ItemPredicate.Builder.create().items(Registries.ITEM, Items.PLAYER_HEAD).build(),
						DataComponentTypes.PROFILE, "", 1
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

	public boolean isOwner(PlayerEntity player) {
		return isOwner(player.getUuid());
	}

	public boolean isAdmin(PlayerEntity player) {
		return isAdmin(player.getUuid());
	}

	public boolean isAdmin(UUID player) {
		return admins.contains(player);
	}

	public boolean canModifyMembers(UUID player) {
		return isAdmin(player) || isOwner(player);
	}

	public boolean canModifyMembers(PlayerEntity player) {
		return canModifyMembers(player.getUuid());
	}

	public boolean isMember(PlayerEntity player) {
		return isMember(player.getUuid());
	}

	public boolean isMember(UUID player) {
		return allowedPlayers.contains(player);
	}

	public boolean isAllowed(UUID player) {
		return canModifyMembers(player) || isMember(player) || balance <= 0;
	}

	public boolean isAllowed(PlayerEntity player) {
		return isAllowed(player.getUuid());
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
			var owner = type.getOwner(stack, zone.getWorld().getRegistryManager()).orElse(null);
			if (owner != null && isAllowed(owner)) {
				long newAmount = balance - (long) type.amount * stack.getCount();
				if (newAmount < 0) {
					newAmount = 0;
				}
				stack.decrement((int) ((balance - newAmount) / type.amount));
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
				stack.decrement((int) ((newAmount - balance) / type.amount));
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

	public record DecrementItem<T>(ItemPredicate predicate, ComponentType<T> uuidComponent, String uuidPath, int amount) {
		public static final Codec<DecrementItem<?>> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ItemPredicate.CODEC.fieldOf("predicate").forGetter(DecrementItem::predicate),
						Registries.DATA_COMPONENT_TYPE.getCodec().fieldOf("uuidComponent").forGetter(DecrementItem::uuidComponent),
						Codec.STRING.fieldOf("uuidPath").forGetter(DecrementItem::uuidPath),
						Codec.INT.fieldOf("amount").forGetter(DecrementItem::amount)
				).apply(instance, DecrementItem::new)
		);

		public Optional<UUID> getOwner(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
			if (!predicate.test(stack)) return Optional.empty();
			return Optional.ofNullable(stack.get(uuidComponent)).flatMap(component -> {
				DataResult<UUID> result = switch (component) {
					case NbtComponent nbt -> nbt.get(getDecoder(uuidPath));
					case NbtCompound nbt -> getDecoder(uuidPath).decode(NbtOps.INSTANCE, NbtOps.INSTANCE.getMap(nbt).getOrThrow());
					case ProfileComponent profile -> profile.id().map(DataResult::success).orElse(DataResult.error(() -> "Profile component not loaded!"));
					default -> {
						var codec = uuidComponent.getCodec();
						if (codec == null) yield DataResult.error(() -> "Cannot fetch UUID from unserializable codec!");
						yield codec.encodeStart(lookup.getOps(JavaOps.INSTANCE), component).flatMap(
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
							return addBetterError(Uuids.CODEC.parse(ops, uuid), path.length);
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

		private static UUID getFromNBT(String uuidPath, NbtCompound nbt) {
			String[] path = uuidPath.split("\\.");
			NbtCompound current = nbt;
			for (int i = 0; i < path.length-1; i++) {
				if (current.contains(path[i], NbtElement.COMPOUND_TYPE)) {
					current = current.getCompound(path[i]);
				}
			}
			String lastKey = path[path.length-1];
			if (current.containsUuid(lastKey)) {
				return current.getUuid(lastKey);
			} else if (current.contains(lastKey, NbtElement.STRING_TYPE)) {
				var string = current.getString(lastKey);
				try {
					return UUID.fromString(string);
				} catch (IllegalArgumentException ignored) {}
				if (string.length() < 32) {
					try {
						var lhs = string.substring(0, 16);
						var rhs = string.substring(16);
						var most = Long.parseLong(lhs, 16);
						var least = Long.parseLong(rhs, 16);
						return new UUID(most, least);
					} catch (StringIndexOutOfBoundsException | NumberFormatException ignored) {}
				}
				return null;
			} else if (
					current.contains(lastKey + "Least", NbtElement.LONG_TYPE) &&
					current.contains(lastKey + "Most", NbtElement.LONG_TYPE)
			) {
				long least = current.getLong(lastKey + "Least");
				long most = current.getLong(lastKey + "Most");
				return new UUID(most, least);
			} else {
				return null;
			}
		}
	}

	@Override
	public Text toText(RegistryWrapper.WrapperLookup lookup) {
		return Text.literal(
				"PlayerOwnedProtectorate[owners=[" +
						owners.stream().map(
								owner -> zone.getWorld().getServer().getUserCache().getByUuid(owner)
										.map(GameProfile::getName).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], admins=[" +
						admins.stream().map(
								owner -> zone.getWorld().getServer().getUserCache().getByUuid(owner)
										.map(GameProfile::getName).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], members=[" +
						allowedPlayers.stream().map(
								owner -> zone.getWorld().getServer().getUserCache().getByUuid(owner)
										.map(GameProfile::getName).orElse(owner.toString())
						).collect(Collectors.joining(", ")) +
						"], balance=" +
						balance +
						", acceptedPaymentItems=[" +
						acceptedPaymentItems.stream().map(
								element -> IncrementItem.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), element).resultOrPartial(
										METAcraftPlots.LOGGER::error
								).map(NbtElement::asString).orElse(null)
						).collect(Collectors.joining(", ")) +
						", acceptedDecrementItems=[" +
						acceptedDecrementItems.stream().map(
								element -> DecrementItem.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), element).resultOrPartial(
										METAcraftPlots.LOGGER::error
								).map(NbtElement::asString).orElse(null)
						).collect(Collectors.joining(", ")) +
						"]"
		);
	}
}
