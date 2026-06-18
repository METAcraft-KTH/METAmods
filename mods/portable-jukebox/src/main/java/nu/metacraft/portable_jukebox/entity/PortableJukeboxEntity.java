package nu.metacraft.portable_jukebox.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;
import nu.metacraft.portable_jukebox.EntityRefHelper;
import nu.metacraft.portable_jukebox.InventoryHelper;
import nu.metacraft.portable_jukebox.PortableJukebox;
import nu.metacraft.portable_jukebox.data.RemovalAware;
import nu.metacraft.portable_jukebox.item.Items;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;
import nu.metacraft.portable_jukebox.item.components.Components;
import nu.metacraft.portable_jukebox.item.components.PortableJukeboxConfiguration;
import nu.metacraft.portable_jukebox.item.components.PortableJukeboxEntityEntry;
import nu.metacraft.portable_jukebox.ItemWithInventoryHelper;

import java.util.*;

public class PortableJukeboxEntity extends Entity implements PolymerEntity, RemovalAware {

	private EntityRef attachment;
	private ItemStack jukebox = ItemStack.EMPTY;

	private final Set<ServerPlayer> hearingPlayers = new HashSet<>();

	public PortableJukeboxEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityTypes.MARKER;
	}

	public void setConnectedEntity(EntityRef entity) {
		this.attachment = entity;
	}

	public Optional<EntityRef> getConnectedEntity() {
		return Optional.ofNullable(attachment);
	}

	private Optional<JukeboxSong> get() {
		return PortableJukeboxItem.getSongFromJukebox(jukebox).map(Holder::value);
	}

	private static final int COLOUR_COUNT = ChatFormatting.OBFUSCATED.ordinal();

	public void setJukebox(ItemStack jukebox) {
		this.jukebox = jukebox;
		get().ifPresentOrElse(song -> {
			jukebox.set(Components.PORTABLE_JUKEBOX_ENTITY, new PortableJukeboxEntityEntry(
					this.getUUID()
			));
			for (var player : hearingPlayers) {
				player.sendOverlayMessage(
						Component.translatable("record.nowPlaying", song.description()).withStyle(
								style -> style.withColor(ChatFormatting.values()[player.getRandom().nextInt(COLOUR_COUNT)])
						)
				);
			}
			var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);
			for (var player : level().players()) {
				((ServerPlayer) player).connection.send(new ClientboundSoundEntityPacket(
						song.soundEvent(), SoundSource.RECORDS, this,
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
			this.absSnapTo(
					pos.x(), pos.y(), pos.z()
			);
		}
		var song = get();
		var config = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_CONFIGURATION, PortableJukeboxConfiguration.DEFAULT);
		if (song.isEmpty() || tickCount > song.get().lengthInTicks() / config.pitch()) {
			discard();
		}
		if (!this.isRemoved() && !this.level().isClientSide() && tickCount % 20 == 0) {
			level().gameEvent(this, GameEvent.JUKEBOX_PLAY, this.position());
			((ServerLevel) level()).sendParticles(
					ParticleTypes.NOTE, getX(), getY() + attachment.getHeight()+0.2, getZ(), 1,
					0, getRandom().nextInt(4) / 24.0f, 0, 1
			);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	private static boolean isValidContainer(ItemStack stack) {
		return ItemWithInventoryHelper.canContainItems(stack) && ItemWithInventoryHelper.getRecursiveInventoryContents(stack).anyMatch(
				PortableJukeboxEntity::isValidStack
		);
	}

	private static boolean isValidStack(ItemStack stack) {
		return stack.is(Items.PORTABLE_JUKEBOX);
	}

	private static Runnable transferInternal(EntityRef from, EntityRef to, UUID toTransfer) {
		if (from != null && to != null && from.get() == to.get()) return () -> {};
		if (from != null) {
			var jukebox = ((ServerLevel) from.getWorld()).getEntity(toTransfer);
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
			var jukebox = from.getWorld().getEntity(toTransfer);
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

	private static Optional<EntityRef> getPortableJukeboxAttachmentNonRecursive(ItemStack stack, Level world) {
		if (stack.has(Components.PORTABLE_JUKEBOX_ENTITY) && !world.isClientSide()) {
			var entry = stack.get(Components.PORTABLE_JUKEBOX_ENTITY);
			var e = world.getEntity(entry.entity());
			if (e instanceof PortableJukeboxEntity jukebox) {
				return jukebox.getConnectedEntity();
			}
		}
		return Optional.empty();
	}

	public static Optional<EntityRef> getPortableJukeboxAttachment(ItemStack stack, Level world) {
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

	public static void transferToItemFromUnknown(ItemStack stack, Level world, ItemEntity itemEntity) {
		getPortableJukeboxAttachment(stack, world).ifPresent(entity -> {
			PortableJukeboxEntity.transfer(
					entity, EntityRef.fromEntity(itemEntity), itemEntity.getItem()
			);
		});
	}

	public static void transferToEntityFromUnknown(ItemStack stack, Entity entity) {
		getPortableJukeboxAttachment(stack, entity.level()).ifPresent(music -> {
			PortableJukeboxEntity.transfer(
					music, EntityRef.fromEntity(entity), stack
			);
		});
	}

	public static void transferInScreenHandlerFromUnknown(ItemStack stack, AbstractContainerMenu handler) {
		if (handler.slots.isEmpty()) return;
		transferToInventoryFromUnknown(stack, handler.slots.getLast().container);
	}

	private static EntityRef getFromInventory(Container inventory) {
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

	public static void transferToInventory(EntityRef from, ItemStack stack, Container inventory) {
		PortableJukeboxEntity.transfer(from, getFromInventory(inventory), stack);
	}

	public static void transferToInventoryFromUnknown(ItemStack stack, Container inventory) {
		var world = InventoryHelper.getWorldFromInventory(inventory);
		if (world.isEmpty()) return;
		getPortableJukeboxAttachment(stack, world.get()).ifPresent(entity -> {
			PortableJukeboxEntity.transfer(
					entity, getFromInventory(inventory), stack
			);
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {

	}

	@Override
	protected void readAdditionalSaveData(ValueInput nbt) {

	}

	@Override
	protected void addAdditionalSaveData(ValueOutput nbt) {

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
		level().gameEvent(this, GameEvent.JUKEBOX_STOP_PLAY, this.position());
		for (var player : hearingPlayers) {
			player.connection.send(new ClientboundRemoveEntitiesPacket(this.getId()));
		}
		hearingPlayers.clear();
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (hearingPlayers.add(player)) {

			var trackers = EntityTrackerHelper.getEntityTrackers((ServerLevel) level());
			var entry = EntityTrackerHelper.getEntry(trackers.get(this.getId()));
			ArrayList<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
			entry.sendPairingData(player, packets::add);
			player.connection.send(new ClientboundBundlePacket(packets));
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (player.level() != this.level() || !this.isAlive()) {
			if (hearingPlayers.remove(player)) {
				player.connection.send(new ClientboundRemoveEntitiesPacket(this.getId()));
			}
		}
	}
}
