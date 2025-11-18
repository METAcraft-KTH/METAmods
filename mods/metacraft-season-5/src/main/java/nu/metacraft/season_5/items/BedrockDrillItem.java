package nu.metacraft.season_5.items;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import nu.metacraft.season_5.items.components.Season5Components;
import org.jetbrains.annotations.NotNull;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Objects;
import java.util.Optional;

public class BedrockDrillItem extends Item implements PolymerItem {

	public BedrockDrillItem(Properties properties) {
		super(properties);
	}

	private boolean isValidBlock(Level level, BlockPos pos, Player player) {
		if (level.dimension() == Level.NETHER && level.mayInteract(player, pos)) {
			if (pos.getY() >= level.getMinY() + level.dimensionType().logicalHeight() - 5) {
				return level.getBlockState(pos).is(Blocks.BEDROCK);
			}
		}
		return false;
	}

	@Override
	public @NotNull InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null) return InteractionResult.FAIL;
		if (isValidBlock(context.getLevel(), context.getClickedPos(), context.getPlayer())) {
			context.getItemInHand().set(Season5Components.DRILL_POSITION, context.getClickedPos());
			context.getPlayer().startUsingItem(context.getHand());
			return InteractionResult.CONSUME;
		}

		return super.useOn(context);
	}

	private Optional<BlockPos> getTargetPosIfValid(Level level, Player player) {
		var targetBlock = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (targetBlock.getType() == HitResult.Type.MISS) {
			return Optional.empty();
		}
		var pos = targetBlock.getBlockPos();
		if (isValidBlock(level, pos, player)) {
			return Optional.of(pos);
		}
		return Optional.empty();
	}

	@Override
	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
		super.onUseTick(level, livingEntity, stack, remainingUseDuration);
		if (livingEntity instanceof ServerPlayer player) {
			var pos = getTargetPosIfValid(level, player);
			if (pos.isEmpty() || !Objects.equals(pos.get(), stack.get(Season5Components.DRILL_POSITION))) {
				player.releaseUsingItem();
			} else {
				var p = pos.get();
				int totalDuration = getUseDuration(stack);
				int progressTicks = totalDuration - remainingUseDuration;
				int progress = Mth.floor(progressTicks * 10.0f / totalDuration);
				int prevProgress = Mth.floor((progressTicks-1) * 10.0f / totalDuration);
				level.playSound(null, p, SoundEvents.STONE_BREAK, SoundSource.PLAYERS);
				if (level instanceof ServerLevel sl) {
					sl.sendParticles(
							new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BEDROCK.defaultBlockState()),
							p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
							10, 0.25, 0.25, 0.25, 1
					);
				}
				if (prevProgress != progress) {
					var packet = new ClientboundBlockDestructionPacket(player.getId(), p, progress);
					if (level.getChunkSource() instanceof ServerChunkCache cache) {
						cache.sendToTrackingPlayersAndSelf(player, packet);
					}
				}
			}
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		var pos = stack.remove(Season5Components.DRILL_POSITION);
		if (!level.isClientSide() && entity instanceof Player player && pos != null) {
			var packet = new ClientboundBlockDestructionPacket(player.getId(), pos, 10);
			if (level.getChunkSource() instanceof ServerChunkCache cache) {
				cache.sendToTrackingPlayersAndSelf(player, packet);
			}
		}
		return false;
	}

	@Override
	public @NotNull ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
		if (livingEntity instanceof Player player) {
			var pos = getTargetPosIfValid(level, player);
			if (pos.isPresent() && Objects.equals(pos.get(), stack.get(Season5Components.DRILL_POSITION))) {
				level.destroyBlock(pos.get(), false);
				stack.remove(Season5Components.DRILL_POSITION);
				stack.consume(1, player);
				return stack;
			}
		}
		return super.finishUsingItem(stack, level, livingEntity);
	}

	@Override
	public @NotNull ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	public int getUseDuration(ItemStack stack) {
		return 1500;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return getUseDuration(stack);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
		return Items.BRICK;
	}
}
