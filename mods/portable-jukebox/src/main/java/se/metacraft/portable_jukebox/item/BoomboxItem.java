package se.metacraft.portable_jukebox.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.block.BoomboxBlocks;
import se.metacraft.portable_jukebox.item.components.Autoplay;
import se.metacraft.portable_jukebox.item.components.BoomboxComponents;
import se.metacraft.portable_jukebox.EntityRefHelper;
import se.metacraft.portable_jukebox.entity.BoomboxEntities;
import se.metacraft.portable_jukebox.gui.BoomboxGui;
import se.metacraft.portable_jukebox.item.components.BoomboxInvData;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BoomboxItem extends BlockItem implements PolymerItem {

	private static final int MAX_TOOLTIP_DISCS = 5;

	public BoomboxItem(Item.Properties settings) {
		super(BoomboxBlocks.BOOMBOX, settings);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.BRICK;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return stack.get(DataComponents.ITEM_MODEL);
	}

	public static void play(ItemStack stack, EntityRef entity) {
		stop(stack, (ServerLevel) entity.getWorld());
		var boomboxPlayer = BoomboxEntities.BOOMBOX.create(entity.getWorld(), EntitySpawnReason.TRIGGERED);
		var pos = entity.getPos();
		boomboxPlayer.setPosRaw(pos.x(), pos.y(), pos.z());
		boomboxPlayer.setConnectedEntity(entity);
		entity.getWorld().addFreshEntity(boomboxPlayer);
		boomboxPlayer.setBoombox(stack);
		EntityRefHelper.addBoombox(entity, boomboxPlayer);
	}

	public static void stop(ItemStack stack, ServerLevel world) {
		Optional.ofNullable(stack.get(BoomboxComponents.BOOMBOX_ENTITY)).ifPresent(entityEntry -> {
			var entity = world.getEntity(entityEntry.entity());
			if (entity != null) {
				entity.discard();
			}
		});
	}

	@Override
	public @NonNull InteractionResult useOn(final @NonNull UseOnContext context) {
		if (context.getPlayer() == null || context.isSecondaryUseActive()) {
			var use = super.useOn(context);
			if (use instanceof InteractionResult.Success s) {
				if (s.swingSource() == InteractionResult.SwingSource.PREDICTED) {
					return new InteractionResult.Success(InteractionResult.SwingSource.SERVER_ONLY, s.itemContext());
				}
				return s;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	public @NonNull InteractionResult use(Level world, Player user, @NonNull InteractionHand hand) {
		var stack = user.getItemInHand(hand);
		if (!world.isClientSide() && !user.isSecondaryUseActive()) {
			var gui = BoomboxGui.create((ServerPlayer) user, stack, EntityRef.fromEntity(user));
			gui.open();
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		super.onDestroyed(entity);
		Optional.ofNullable(entity.getItem().get(BoomboxComponents.BOOMBOX)).ifPresent(disc -> {
			Containers.dropItemStack(entity.level(), entity.getX(), entity.getY(), entity.getZ(), disc.getItem());
		});
		for (var stored : getStorage(entity.getItem()).nonEmptyItems()) {
			Containers.dropItemStack(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stored.create());
		}
	}

	@Override
	public boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(
			BlockState state, ServerPlayer player, InteractionHand hand, ItemStack stack, ServerLevel world, BlockHitResult blockHitResult
	) {
		return true;
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		var discs = Stream.concat(
				getDiscFromBoombox(stack).stream(), reStream(getStorage(stack)).map(ItemStackTemplate::create)
		).toList();
		for (var disc : discs.subList(0, Math.min(discs.size(), MAX_TOOLTIP_DISCS))) {
			tooltip.add(discName(disc).copy().withStyle(ChatFormatting.GRAY));
		}
		if (discs.size() > MAX_TOOLTIP_DISCS) {
			tooltip.add(Component.translatable("container.shulkerBox.more", discs.size() - MAX_TOOLTIP_DISCS)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The disc's own name is just "Music Disc". */
	private static Component discName(ItemStack disc) {
		var playable = disc.get(DataComponents.JUKEBOX_PLAYABLE);
		return playable != null ? playable.song().value().description() : disc.getHoverName();
	}

	public static boolean hasDiscsInStorage(ItemStack boombox) {
		return reStream(getStorage(boombox)).findAny().isPresent();
	}

	public static boolean hasDiscs(ItemStack boombox) {
		return getDiscFromBoombox(boombox).isPresent() || hasDiscsInStorage(boombox);
	}

	public static Stream<ItemStackTemplate> reStream(ItemContainerContents items) {
		return StreamSupport.stream(items.nonEmptyItems().spliterator(), false);
	}

	public static ItemContainerContents getStorage(ItemStack boombox) {
		return boombox.getOrDefault(BoomboxComponents.BOOMBOX_STORAGE, ItemContainerContents.EMPTY);
	}

	public static NonNullList<ItemStack> getStorageList(ItemStack boombox) {
		NonNullList<ItemStack> list = NonNullList.withSize(
			boombox.getOrDefault(BoomboxComponents.STORAGE_SIZE, 0),
			ItemStack.EMPTY
		);
		getStorage(boombox).copyInto(list);
		return list;
	}

	public static void setStorage(ItemStack boombox, ItemContainerContents items) {
		if (reStream(items).findAny().isEmpty()) {
			boombox.remove(BoomboxComponents.BOOMBOX_STORAGE);
		} else {
			boombox.set(BoomboxComponents.BOOMBOX_STORAGE, items);
		}
	}

	public static Optional<ItemStack> getDiscFromBoombox(ItemInstance stack) {
		return stack.getOrDefault(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY).getNonEmptyItem();
	}

	public static Optional<Holder<JukeboxSong>> getSongFromBoombox(ItemInstance stack) {
		return getDiscFromBoombox(stack).flatMap(JukeboxSong::fromStack);
	}

	public static int getComparatorOutput(ItemStack stack) {
		return getSongFromBoombox(stack).map(song -> song.value().comparatorOutput()).orElse(0);
	}

	public static void updateStackChange(EntityRef entity, ItemStack prevDisc, ItemStack stack) {
		if (stack.has(BoomboxComponents.BOOMBOX_ENTITY)) {
			var currentSong = getSongFromBoombox(stack);
			if (
					currentSong.isPresent() &&
					!JukeboxSong.fromStack(prevDisc).equals(currentSong)
			) {
				play(stack, entity);
			}
		}
	}

	public static void updateRedstone(EntityRef entity, ItemStack stack) {
		if (entity.isReceivingRedstonePower()) {
			if (!stack.has(BoomboxComponents.BOOMBOX_ENTITY) && BoomboxItem.getDiscFromBoombox(stack).isPresent()) {
				play(stack, entity);
			} else if (BoomboxItem.hasDiscsInStorage(stack)) {
				Autoplay.playFromStorage(stack, entity, entity.getRandom());
			}
		}
	}
}
