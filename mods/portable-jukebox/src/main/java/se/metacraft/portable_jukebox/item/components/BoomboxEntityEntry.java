package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import se.metacraft.portable_jukebox.ItemWithInventoryHelper;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

public record BoomboxEntityEntry(UUID entity) {

	public static final Codec<BoomboxEntityEntry> CODEC = UUIDUtil.LENIENT_CODEC.xmap(
			BoomboxEntityEntry::new, BoomboxEntityEntry::entity
	);

	public static Optional<UUID> getWithoutInventories(ItemStack stack) {
		return Optional.ofNullable(stack.get(BoomboxComponents.BOOMBOX_ENTITY)).map(BoomboxEntityEntry::entity);
	}

	public static Stream<UUID> get(ItemStack stack) {
		return ItemWithInventoryHelper.getAllComponents(stack, BoomboxComponents.BOOMBOX_ENTITY).map(BoomboxEntityEntry::entity);
	}

}
