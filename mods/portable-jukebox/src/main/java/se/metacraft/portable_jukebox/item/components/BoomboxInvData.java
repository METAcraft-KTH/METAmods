package se.metacraft.portable_jukebox.item.components;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.item.component.GrowableMutableContainer;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record BoomboxInvData(Optional<ItemStackTemplate> item) implements ContainerComponent<BoomboxInvData> {

	public static final BoomboxInvData EMPTY = new BoomboxInvData(Optional.empty());

	public static final Codec<BoomboxInvData> CODEC = Codec.either(
		ItemStackTemplate.CODEC.xmap(
			stack -> new BoomboxInvData(Optional.of(stack)),
			inv -> inv.item.orElseThrow()
		),
		MapCodec.unitCodec(EMPTY)
	).xmap(
		either -> either.map(l -> l, r -> r),
		inv -> inv.item().map(i -> Either.<BoomboxInvData, BoomboxInvData>left(inv)).orElse(Either.right(EMPTY))
	);

	@Override
	public @NonNull Stream<ItemStack> itemCopies() {
		return item.stream().map(ItemStackTemplate::create);
	}

	@Override
	public int size() {
		return item.isPresent() ? 1 : 0;
	}

	@Override
	public @NonNull BoomboxInvData copyWithContents(@NonNull Stream<ItemStack> newContents) {
		return new BoomboxInvData(newContents.findAny().map(ItemStackTemplate::fromStack));
	}

	@Override
	public @NonNull MutableBoombox asMutable() {
		return new MutableBoombox(item.stream().map(ItemStackTemplate::create).toList());
	}

	public BoomboxInvData withItem(ItemStack item) {
		return new BoomboxInvData(item.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(item)));
	}

	public ItemStack getItem() {
		return getNonEmptyItem().orElse(ItemStack.EMPTY);
	}

	public Optional<ItemStack> getNonEmptyItem() {
		return item.map(ItemStackTemplate::create);
	}

	public static class MutableBoombox extends GrowableMutableContainer<BoomboxInvData> {

		public MutableBoombox(List<ItemStack> items) {
			super(items);
		}

		@Override
		public boolean canInsertNewSlots() {
			return items.isEmpty();
		}

		@Override
		protected boolean setItem(final int slot, final @NonNull ItemStack itemStack) {
			if (!itemStack.has(DataComponents.JUKEBOX_PLAYABLE) && !itemStack.isEmpty()) return false;
			return super.setItem(slot, itemStack);
		}

		@Override
		public @NonNull BoomboxInvData toImmutable() {
			return new BoomboxInvData(items.stream().filter(i -> !i.isEmpty()).map(s -> s.copyWithCount(1)).findAny().map(ItemStackTemplate::fromStack));
		}
	}
}
