package se.datasektionen.mc.portable_jukebox.item;

import eu.pb4.polymer.core.api.item.PolymerHeadBlockItem;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.block.Blocks;
import se.datasektionen.mc.portable_jukebox.item.components.Components;
import se.datasektionen.mc.portable_jukebox.EntityRefHelper;
import se.datasektionen.mc.portable_jukebox.entity.Entities;
import se.datasektionen.mc.portable_jukebox.gui.PortableJukeboxGui;

import java.util.Optional;

public class PortableJukeboxItem extends PolymerHeadBlockItem {

	public PortableJukeboxItem(Settings settings) {
		super(Blocks.PORTABLE_JUKEBOX, settings);
	}

	public static void play(ItemStack stack, EntityRef entity) {
		stop(stack, (ServerWorld) entity.getWorld());
		var jukeboxPlayer = Entities.PORTABLE_JUKEBOX.create(entity.getWorld());
		var pos = entity.getPos();
		jukeboxPlayer.setPos(pos.getX(), pos.getY(), pos.getZ());
		jukeboxPlayer.setEntity(entity);
		entity.getWorld().spawnEntity(jukeboxPlayer);
		jukeboxPlayer.setJukebox(stack);
		EntityRefHelper.addPortableJukebox(entity, jukeboxPlayer);
	}

	public static void stop(ItemStack stack, ServerWorld world) {
		Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX_ENTITY)).ifPresent(entityEntry -> {
			var entity = world.getEntity(entityEntry.entity());
			if (entity != null) {
				entity.discard();
			}
		});
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		var stack = user.getStackInHand(hand);
		if (!world.isClient()) {
			var gui = PortableJukeboxGui.create((ServerPlayerEntity) user, stack, EntityRef.fromEntity(user));
			gui.open();
			return TypedActionResult.success(stack);
		}
		return TypedActionResult.pass(stack);
	}

	@Override
	public void onItemEntityDestroyed(ItemEntity entity) {
		super.onItemEntityDestroyed(entity);
		Optional.ofNullable(entity.getStack().get(Components.PORTABLE_JUKEBOX)).ifPresent(disc -> {
			ItemScatterer.spawn(entity.getWorld(), entity.getX(), entity.getY(), entity.getZ(), disc);
		});
	}

	public static Optional<ItemStack> getDiscFromJukebox(ItemStack stack) {
		return Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX));
	}

	public static Optional<RegistryEntry<JukeboxSong>> getSongFromJukebox(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		return getDiscFromJukebox(stack).flatMap(disc -> JukeboxSong.getSongEntryFromStack(lookup, disc));
	}

	public static int getComparatorOutput(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		return getSongFromJukebox(stack, lookup).map(song -> song.value().comparatorOutput()).orElse(0);
	}

	public static void updateStackChange(EntityRef entity, ItemStack prevDisc, ItemStack stack) {
		if (stack.contains(Components.PORTABLE_JUKEBOX_ENTITY)) {
			var currentSong = getSongFromJukebox(stack, entity.getRegistryManager());
			if (
					currentSong.isPresent() &&
					!JukeboxSong.getSongEntryFromStack(entity.getRegistryManager(), prevDisc).equals(currentSong)
			) {
				play(stack, entity);
			}
		}
	}

	public static void updateRedstone(EntityRef entity, ItemStack stack) {
		if (entity.isReceivingRedstonePower()) {
			if (!stack.contains(Components.PORTABLE_JUKEBOX_ENTITY)) {
				play(stack, entity);
			}
		}
	}
}
