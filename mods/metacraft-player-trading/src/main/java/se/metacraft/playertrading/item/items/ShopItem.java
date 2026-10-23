package se.metacraft.playertrading.item.items;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import se.metacraft.lib.util.helper.GameProfileHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.metacraft.playertrading.block.blocks.BaseShopBlock;

public class ShopItem extends StandingAndWallBlockItem implements PolymerItem {

	public ShopItem(Block block, Block wallBlock, Properties properties) {
		super(block, wallBlock, Direction.DOWN, properties);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.FLINT;
	}

	@Override
	public @NonNull InteractionResult place(final @NonNull BlockPlaceContext placeContext) {
		var result = super.place(placeContext);
		if (result == InteractionResult.SUCCESS) {
			if (placeContext.getPlayer() instanceof ServerPlayer sp) {
				var level = placeContext.getLevel();
				var pos = placeContext.getClickedPos();
				var placedState = level.getBlockState(pos);
				SoundType soundType = placedState.getSoundType();
				sp.connection.send(new ClientboundSoundPacket(
					BuiltInRegistries.SOUND_EVENT.wrapAsHolder(this.getPlaceSound(placedState)),
					SoundSource.BLOCKS, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F,
					level.getRandom().nextInt()
				));
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		return result;
	}

	@Override
	@Nullable
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return Items.PLAYER_HEAD.components().get(DataComponents.ITEM_MODEL);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		out.set(
			DataComponents.PROFILE,
			GameProfileHelper.staticComponentBuilder().withServersideSkin(BaseShopBlock.SKIN).build()
		);
	}

}
