package se.datasektionen.mc.portable_jukebox.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;
import se.datasektionen.mc.portable_jukebox.EntityRefHelper;
import se.datasektionen.mc.portable_jukebox.InventoryHelper;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;
import se.datasektionen.mc.portable_jukebox.data.RemovalAware;
import se.datasektionen.mc.portable_jukebox.item.Items;
import se.datasektionen.mc.portable_jukebox.item.PortableJukeboxItem;
import se.datasektionen.mc.portable_jukebox.item.components.Components;
import se.datasektionen.mc.portable_jukebox.item.components.PortableJukeboxConfiguration;
import se.datasektionen.mc.portable_jukebox.item.components.PortableJukeboxEntityEntry;
import se.datasektionen.mc.portable_jukebox.ItemWithInventoryHelper;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;

public class PortableJukeboxEntity extends Entity implements PolymerEntity, RemovalAware {

	private EntityRef attachment;
	private ItemStack jukebox = ItemStack.EMPTY;

	private final Set<ServerPlayerEntity> hearingPlayers = new HashSet<>();

	public PortableJukeboxEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.MARKER;
	}

	public void setEntity(EntityRef entity) {
		this.attachment = entity;
	}

	public Optional<EntityRef> getEntity() {
		return Optional.ofNullable(attachment);
	}

	private Optional<JukeboxSong> get() {
		return PortableJukeboxItem.getSongFromJukebox(jukebox, getRegistryManager()).map(RegistryEntry::value);
	}

	private static final int MAX_COLOUR_INDEX = Arrays.stream(Formatting.values()).mapToInt(Formatting::getColorIndex).max().orElse(0);

	public void setJukebox(ItemStack jukebox) {
		this.jukebox = jukebox;
		get().ifPresentOrElse(song -> {
			jukebox.set(Components.PORTABLE_JUKEBOX_ENTITY, new PortableJukeboxEntityEntry(
					this.getUuid()
			));
			for (var player : hearingPlayers) {
				player.sendMessage(
						Text.translatable("record.nowPlaying", song.description()).styled(
								style -> style.withColor(Formatting.byColorIndex(player.getRandom().nextInt(MAX_COLOUR_INDEX+1)))
						), true
				);
			}
			var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);
			for (var player : getWorld().getPlayers()) {
				((ServerPlayerEntity) player).networkHandler.sendPacket(new PlaySoundFromEntityS2CPacket(
						song.soundEvent(), SoundCategory.RECORDS, this,
						config.volume(), config.pitch(), this.getRandom().nextLong()
				));
			}
		}, () -> {
			this.jukebox = ItemStack.EMPTY;
		});
	}

	public void fixStack(ItemStack newStack) {
		this.jukebox = newStack;
	}

	@Override
	public void tick() {
		super.tick();
		if (attachment == null || attachment.isRemoved()) {
			discard();
		} else {
			var pos = attachment.getPos();
			this.updatePosition(
					pos.getX(), pos.getY(), pos.getZ()
			);
		}
		var song = get();
		var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);
		if (song.isEmpty() || age > song.get().getLengthInTicks() / config.pitch()) {
			discard();
		}
		if (!this.isRemoved() && !this.getWorld().isClient() && age % 20 == 0) {
			getWorld().emitGameEvent(this, GameEvent.JUKEBOX_PLAY, this.getPos());
			((ServerWorld) getWorld()).spawnParticles(
					ParticleTypes.NOTE, getX(), getY() + attachment.getHeight()+0.2, getZ(), 1,
					0, getRandom().nextInt(4) / 24.0f, 0, 1
			);
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	private static boolean isValidContainer(ItemStack stack) {
		return ItemWithInventoryHelper.canContainItems(stack) && ItemWithInventoryHelper.getRecursiveInventoryContents(stack).anyMatch(
				PortableJukeboxEntity::isValidStack
		);
	}

	private static boolean isValidStack(ItemStack stack) {
		return stack.isOf(Items.PORTABLE_JUKEBOX);
	}

	private static Runnable transferInternal(EntityRef from, EntityRef to, UUID toTransfer) {
		if (from != null && to != null && from.get() == to.get()) return () -> {};
		if (from != null) {
			var jukebox = ((ServerWorld) from.getWorld()).getEntity(toTransfer);
			if (!(jukebox instanceof PortableJukeboxEntity actualJukebox)) {
				PortableJukebox.LOGGER.error(
						Optional.ofNullable(to).map(
								EntityRef::getBackendName
						).map(t -> "Unable to move jukebox sound source from " + from.getBackendName() + " to " + t).orElse(
								"Unable to remove jukebox sound source from " + from.getBackendName()
						)
				);
				return () -> {};
			}
			if (to != null) {
				EntityRefHelper.addPortableJukebox(to, actualJukebox);
				EntityRefHelper.removePortableJukebox(from, actualJukebox);
			} else {
				return actualJukebox::discard;
			}
		}
		return () -> {};
	}

	private static void transferSingle(EntityRef from, EntityRef to, ItemStack resultStack) {
		PortableJukeboxEntityEntry.getWithoutInventories(resultStack).ifPresent(toTransfer -> {
			var after = transferInternal(from, to, toTransfer);
			var jukebox = ((ServerWorld) from.getWorld()).getEntity(toTransfer);
			if (jukebox instanceof PortableJukeboxEntity j) {
				j.fixStack(resultStack);
			}
			after.run();
		});
	}

	private static void transferNonRecursive(EntityRef from, EntityRef to, ItemStack resultStack) {
		if (isValidStack(resultStack)) {
			transferSingle(from, to, resultStack);
		}
	}

	public static void transfer(EntityRef from, EntityRef to, ItemStack resultStack) {
		transferNonRecursive(from, to, resultStack);
		if (isValidContainer(resultStack)) {
			ItemWithInventoryHelper.getRecursiveInventoryContents(resultStack).forEach(stack -> {
				transferNonRecursive(from, to, stack);
			});
		}
	}

	public static void transfer2Way(EntityRef entity1, ItemStack stack1, EntityRef entity2, ItemStack stack2) {
		if (isValidStack(stack1) || isValidContainer(stack1)) {
			PortableJukeboxEntityEntry.get(stack1).forEach(toTransfer -> {
				transferInternal(entity1, entity2, toTransfer).run();
			});
		}
		if (isValidStack(stack2) || isValidContainer(stack2)) {
			PortableJukeboxEntityEntry.get(stack2).forEach(toTransfer -> {
				transferInternal(entity2, entity1, toTransfer).run();
			});
		}
	}

	private static Optional<EntityRef> getPortableJukeboxAttachmentNonRecursive(ItemStack stack, World world) {
		if (stack.contains(Components.PORTABLE_JUKEBOX_ENTITY) && !world.isClient()) {
			var entry = stack.get(Components.PORTABLE_JUKEBOX_ENTITY);
			var e = ((ServerWorld) world).getEntity(entry.entity());
			if (e instanceof PortableJukeboxEntity jukebox) {
				return jukebox.getEntity();
			}
		}
		return Optional.empty();
	}

	public static Optional<EntityRef> getPortableJukeboxAttachment(ItemStack stack, World world) {
		var current = getPortableJukeboxAttachmentNonRecursive(stack, world);
		if (current.isPresent()) return current;
		//If it is an item with a container, all the items will have the same attachment, so it doesn't matter which one we take.
		//We do not have to worry about components pointing to nonexistent entities, since they will return empty and therefore not be an option.
		if (ItemWithInventoryHelper.canContainItems(stack)) {
			return ItemWithInventoryHelper.getRecursiveInventoryContents(stack).flatMap(
					content -> getPortableJukeboxAttachmentNonRecursive(content, world).stream()
			).findAny();
		}
		return Optional.empty();
	}

	public static void transferToItemFromUnknown(ItemStack stack, World world, ItemEntity itemEntity) {
		getPortableJukeboxAttachment(stack, world).ifPresent(entity -> {
			PortableJukeboxEntity.transfer(
					entity, EntityRef.fromEntity(itemEntity), itemEntity.getStack()
			);
		});
	}

	public static void transferToEntityFromUnknown(ItemStack stack, Entity entity) {
		getPortableJukeboxAttachment(stack, entity.getWorld()).ifPresent(music -> {
			PortableJukeboxEntity.transfer(
					music, EntityRef.fromEntity(entity), stack
			);
		});
	}

	public static void transferInScreenHandlerFromUnknown(ItemStack stack, ScreenHandler handler) {
		if (handler.slots.isEmpty()) return;
		transferToInventoryFromUnknown(stack, handler.slots.getLast().inventory);
	}

	private static EntityRef getFromInventory(Inventory inventory) {
		return InventoryHelper.getEntityFromInventory(inventory).map(ref -> {
			//Since ender chests store the items in some sort of player-specific dimension, it makes sense that the music
			//would stop when you close the lid. Also, it would cause issues if it plays from a specific ender chest
			//and the player picks it up at another ender chest.
			if (ref.getBlock().isPresent() && ref.getBlock().get() instanceof EnderChestBlockEntity) {
				return null;
			}
			return ref;
		}).orElse(null);
	}

	public static void transferToInventory(EntityRef from, ItemStack stack, Inventory inventory) {
		PortableJukeboxEntity.transfer(from, getFromInventory(inventory), stack);
	}

	public static void transferToInventoryFromUnknown(ItemStack stack, Inventory inventory) {
		var world = InventoryHelper.getWorldFromInventory(inventory);
		if (world.isEmpty()) return;
		getPortableJukeboxAttachment(stack, world.get()).ifPresent(entity -> {
			PortableJukeboxEntity.transfer(
					entity, getFromInventory(inventory), stack
			);
		});
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

	}

	@Override
	protected void readCustomData(ReadView nbt) {

	}

	@Override
	protected void writeCustomData(WriteView nbt) {

	}

	@Override
	public void onEntityRemoved(RemovalReason reason) {
		if (attachment != null) {
			EntityRefHelper.removePortableJukebox(attachment, this);
		}
		jukebox.remove(Components.PORTABLE_JUKEBOX_ENTITY);
		if (attachment != null) {
			attachment.onUpdate();
		}
		getWorld().emitGameEvent(this, GameEvent.JUKEBOX_STOP_PLAY, this.getPos());
		for (var player : hearingPlayers) {
			player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(this.getId()));
		}
		hearingPlayers.clear();
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		if (hearingPlayers.add(player)) {

			var trackers = EntityTrackerHelper.getEntityTrackers((ServerWorld) getWorld());
			var entry = EntityTrackerHelper.getEntry(trackers.get(this.getId()));
			ArrayList<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
			entry.sendPackets(player, packets::add);
			player.networkHandler.sendPacket(new BundleS2CPacket(packets));
		}
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		if (player.getWorld() != this.getWorld() || !this.isAlive()) {
			if (hearingPlayers.remove(player)) {
				player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(this.getId()));
			}
		}
	}
}
