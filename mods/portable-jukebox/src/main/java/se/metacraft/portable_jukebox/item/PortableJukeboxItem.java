package se.metacraft.portable_jukebox.item;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.PortableJukebox;
import se.metacraft.portable_jukebox.block.Blocks;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.EntityRefHelper;
import se.metacraft.portable_jukebox.entity.Entities;
import se.metacraft.portable_jukebox.gui.PortableJukeboxGui;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;

import java.util.List;
import java.util.Optional;

public class PortableJukeboxItem extends FixedPolymerHeadBlockItem {

	public PortableJukeboxItem(net.minecraft.world.item.Item.Properties settings) {
		super(Blocks.PORTABLE_JUKEBOX, settings);
	}

	/**
	 * The jukebox model for a tier. The Blockbench models are numbered from 1, so the base jukebox
	 * (tier 0) uses {@code portable_jukebox_tier_1}.
	 */
	public static Identifier model(int tier) {
		return PortableJukebox.getID("portable_jukebox_tier_" + (Math.clamp(tier, 0, JukeboxUpgrades.MAX_TIER) + 1));
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model(JukeboxUpgrades.getTier(stack));
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
			Containers.dropItemStack(entity.level(), entity.getX(), entity.getY(), entity.getZ(), disc.getItem());
		});
		for (var stored : JukeboxUpgrades.getStorage(entity.getItem())) {
			if (!stored.isEmpty()) {
				Containers.dropItemStack(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stored);
			}
		}
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		int tier = JukeboxUpgrades.getTier(stack);
		if (tier > 0) {
			tooltip.add(Component.translatable("portable_jukebox.tooltip.tier", tier, JukeboxUpgrades.MAX_TIER).withStyle(ChatFormatting.GRAY));
		}
		long stored = JukeboxUpgrades.getStorage(stack).stream().filter(s -> !s.isEmpty()).count();
		if (stored > 0) {
			tooltip.add(Component.translatable("portable_jukebox.tooltip.stored", stored).withStyle(ChatFormatting.GRAY));
		}
	}

	public static Optional<ItemStack> getDiscFromJukebox(ItemInstance stack) {
		return stack.getOrDefault(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY).getNonEmptyItem();
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
