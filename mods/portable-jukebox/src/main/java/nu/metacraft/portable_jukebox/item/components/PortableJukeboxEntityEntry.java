package nu.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import nu.metacraft.portable_jukebox.ItemWithInventoryHelper;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

public record PortableJukeboxEntityEntry(UUID entity) {

	public static final Codec<PortableJukeboxEntityEntry> CODEC = UUIDUtil.LENIENT_CODEC.xmap(
			PortableJukeboxEntityEntry::new, PortableJukeboxEntityEntry::entity
	);

	public static Optional<UUID> getWithoutInventories(ItemStack stack) {
		return Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX_ENTITY)).map(PortableJukeboxEntityEntry::entity);
	}

	public static Stream<UUID> get(ItemStack stack) {
		return ItemWithInventoryHelper.getAllComponents(stack, Components.PORTABLE_JUKEBOX_ENTITY).map(PortableJukeboxEntityEntry::entity);
	}

}
