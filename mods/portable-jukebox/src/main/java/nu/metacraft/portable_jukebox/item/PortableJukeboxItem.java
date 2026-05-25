package nu.metacraft.portable_jukebox.item;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.block.Blocks;
import nu.metacraft.portable_jukebox.item.components.Components;
import nu.metacraft.portable_jukebox.EntityRefHelper;
import nu.metacraft.portable_jukebox.entity.Entities;
import nu.metacraft.portable_jukebox.gui.PortableJukeboxGui;

import java.util.Optional;

public class PortableJukeboxItem extends FixedPolymerHeadBlockItem {

	public PortableJukeboxItem(net.minecraft.world.item.Item.Properties settings) {
		super(Blocks.PORTABLE_JUKEBOX, settings);
	}

	public static void play(ItemStack stack, EntityRef entity) {
		stop(stack, (ServerLevel) entity.getWorld());
		var jukeboxPlayer = Entities.PORTABLE_JUKEBOX.create(entity.getWorld(), EntitySpawnReason.TRIGGERED);
		var pos = entity.getPos();
		jukeboxPlayer.setPosRaw(pos.x(), pos.y(), pos.z());
		jukeboxPlayer.setConnectedEntity(entity);
		entity.getWorld().addFreshEntity(jukeboxPlayer);
		jukeboxPlayer.setJukebox(stack);
		EntityRefHelper.addPortableJukebox(entity, jukeboxPlayer);
	}

	public static void stop(ItemStack stack, ServerLevel world) {
		Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX_ENTITY)).ifPresent(entityEntry -> {
			var entity = world.getEntity(entityEntry.entity());
			if (entity != null) {
				entity.discard();
			}
		});
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		var stack = user.getItemInHand(hand);
		if (!world.isClientSide()) {
			var gui = PortableJukeboxGui.create((ServerPlayer) user, stack, EntityRef.fromEntity(user));
			gui.open();
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		super.onDestroyed(entity);
		Optional.ofNullable(entity.getItem().get(Components.PORTABLE_JUKEBOX)).ifPresent(disc -> {
			Containers.dropItemStack(entity.level(), entity.getX(), entity.getY(), entity.getZ(), disc);
		});
	}

	public static Optional<ItemStack> getDiscFromJukebox(ItemInstance stack) {
		return Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX));
	}

	public static Optional<Holder<JukeboxSong>> getSongFromJukebox(ItemInstance stack) {
		return getDiscFromJukebox(stack).flatMap(JukeboxSong::fromStack);
	}

	public static int getComparatorOutput(ItemStack stack) {
		return getSongFromJukebox(stack).map(song -> song.value().comparatorOutput()).orElse(0);
	}

	public static void updateStackChange(EntityRef entity, ItemStack prevDisc, ItemStack stack) {
		if (stack.has(Components.PORTABLE_JUKEBOX_ENTITY)) {
			var currentSong = getSongFromJukebox(stack);
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
			if (!stack.has(Components.PORTABLE_JUKEBOX_ENTITY)) {
				play(stack, entity);
			}
		}
	}
}
