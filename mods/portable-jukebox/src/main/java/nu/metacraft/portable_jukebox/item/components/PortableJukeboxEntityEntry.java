package nu.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Uuids;
import nu.metacraft.portable_jukebox.ItemWithInventoryHelper;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public record PortableJukeboxEntityEntry(UUID entity) {

	public static final Codec<PortableJukeboxEntityEntry> CODEC = Uuids.STRICT_CODEC.xmap(
			PortableJukeboxEntityEntry::new, PortableJukeboxEntityEntry::entity
	);

	public static Optional<UUID> getWithoutInventories(ItemStack stack) {
		return Optional.ofNullable(stack.get(Components.PORTABLE_JUKEBOX_ENTITY)).map(PortableJukeboxEntityEntry::entity);
	}

	public static Stream<UUID> get(ItemStack stack) {
		return ItemWithInventoryHelper.getAllComponents(stack, Components.PORTABLE_JUKEBOX_ENTITY).map(PortableJukeboxEntityEntry::entity);
	}

}
