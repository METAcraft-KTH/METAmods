package nu.metacraft.core.item.items;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.extensions.BlockEntityExtensions;
import nu.metacraft.core.extensions.ServerPlayerExtensions;
import nu.metacraft.core.util.helper.PlayerInventoryHelper;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Wrench extends Item implements PolymerItem {

	private static final ParticleOptions RED_DUST = new DustParticleOptions(
			ARGB.colorFromFloat(1, 1, 0, 0), 1
	);

	public Wrench(net.minecraft.world.item.Item.Properties settings) {
		super(settings);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
		return Items.BRICK;
	}


	private Component getBlockText(boolean newState) {
		if (newState) {
			return Component.translatableWithFallback(
					"item.metacraft.wrench.block.on",
					"The block is now movable by pistons"
			).withStyle(style -> style.applyFormat(ChatFormatting.GREEN));
		} else {
			return Component.translatableWithFallback(
					"item.metacraft.wrench.block.off",
					"The block is no longer movable by pistons"
			).withStyle(style -> style.applyFormat(ChatFormatting.RED));
		}
	}

	private Component getPlayerText(boolean newState) {
		if (newState) {
			return Component.translatableWithFallback(
					"item.metacraft.wrench.player.on",
					"All block entities you place will now be movable by pistons"
			).withStyle(style -> style.applyFormat(ChatFormatting.GREEN));
		} else {
			return Component.translatableWithFallback(
					"item.metacraft.wrench.player.off",
					"All block entities you place will no longer be movable by pistons"
			).withStyle(style -> style.applyFormat(ChatFormatting.RED));
		}
	}

	private Component getTooltipText(boolean currentState) {
		Component state;
		if (currentState) {
			state = Component.translatableWithFallback(
					"item.metacraft.wrench.tooltip.on",
					"Movable"
			).withStyle(style -> style.applyFormat(ChatFormatting.GREEN));
		} else {
			state = Component.translatableWithFallback(
					"item.metacraft.wrench.tooltip.off",
					"Not Movable"
			).withStyle(style -> style.applyFormat(ChatFormatting.RED));
		}
		return Component.translatableWithFallback(
				"item.metacraft.wrench.tooltip",
				"Default: " + state.getString(),
				state
		);
	}

	private void spawnParticlesForPlayer(
			ServerLevel world, Player player, ParticleOptions particle,
			double x, double y, double z, int count,
			float xOffset, float yOffset, float zOffset, float speed
	) {
		for (var p : world.players()) {
			world.sendParticles(
					p, p == player, x, y, z,
					new ClientboundLevelParticlesPacket(
							particle, p == player, p == player,
							x, y, z, xOffset, yOffset, zOffset,
							speed, count
					)
			);
		}
	}

	public static boolean canUse(Level world, BlockPos pos) {
		return world.getBlockEntity(pos) != null;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel() instanceof ServerLevel world) {
			var entity = context.getLevel().getBlockEntity(context.getClickedPos());
			if (entity != null) {
				boolean newState = !((BlockEntityExtensions) entity).metacraft_core$isMovable();
				((BlockEntityExtensions) entity).metacraft_core$setMovable(newState);
				context.getPlayer().sendOverlayMessage(
						getBlockText(newState)
				);
				var pos = Vec3.atCenterOf(context.getClickedPos());
				if (newState) {
					spawnParticlesForPlayer(
							world, context.getPlayer(),
							ParticleTypes.HAPPY_VILLAGER,
							pos.x(), pos.y(), pos.z(), 50,
							0.45f, 0.45f, 0.45f, 1
					);
				} else {
					spawnParticlesForPlayer(
							world, context.getPlayer(),
							RED_DUST,
							pos.x(), pos.y(), pos.z(), 50,
							0.4f, 0.4f, 0.4f, 0.1f
					);
				}
				world.playSound(
						null,
						pos.x(), pos.y(), pos.z(),
						newState ? SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON : SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
						SoundSource.BLOCKS, 1, 1
				);
				context.getPlayer().getCooldowns().addCooldown(context.getItemInHand(), 1); //Sneak-clicking on blocks should not change default state.
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		return super.useOn(context);
	}



	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		if (user.isShiftKeyDown() && world instanceof ServerLevel) {
			var newState = !((ServerPlayerExtensions) user).metacraft_core$areBlocksPistonMovable();
			((ServerPlayerExtensions) user).metacraft_core$setBlocksPistonMovable(newState);
			user.sendOverlayMessage(
					getPlayerText(newState)
			);
			world.playSound(
					null,
					user.getX(), user.getY(), user.getZ(),
					newState ? SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON : SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
					SoundSource.PLAYERS, 1, 1
			);
			PlayerInventoryHelper.syncHandStack(user, hand); //Update tooltip.
			return InteractionResult.SUCCESS_SERVER;
		}
		return super.use(world, user, hand);
	}

	private static final int SCAN_RADIUS = 10;

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, world, entity, slot); //selected is false in offhand.
		if (entity instanceof ServerPlayer player && entity.isShiftKeyDown() && slot != null && slot.getType() == EquipmentSlot.Type.HAND) {
			int minX = SectionPos.blockToSectionCoord(player.getBlockX() - SCAN_RADIUS);
			int minZ = SectionPos.blockToSectionCoord(player.getBlockZ() - SCAN_RADIUS);
			int maxX = SectionPos.blockToSectionCoord(player.getBlockX() + SCAN_RADIUS);
			int maxZ = SectionPos.blockToSectionCoord(player.getBlockZ() + SCAN_RADIUS);

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					for (var pos : world.getChunk(x, z).getBlockEntitiesPos()) {
						if (pos.closerToCenterThan(player.position(), SCAN_RADIUS)) {
							boolean movable = ((BlockEntityExtensions) world.getBlockEntity(pos)).metacraft_core$isMovable();
							if (movable) {
								player.level().sendParticles(
										player, ParticleTypes.HAPPY_VILLAGER, true, true,
										pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5,
										0.45f, 0.45f, 0.45f, 1
								);
							} else {
								player.level().sendParticles(
										player, RED_DUST, true, true,
										pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5,
										0.4f, 0.4f, 0.4f, 0.1f
								);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		var profile = context.get(PacketContext.GAME_PROFILE);
		if (profile == null) return;
		var server = context.get(PacketContext.SERVER_INSTANCE);
		if (server == null) return;
		var player = server.getPlayerList().getPlayer(profile.id());
		if (player != null) {
			tooltip.add(
					getTooltipText(
							((ServerPlayerExtensions) player).metacraft_core$areBlocksPistonMovable()
					)
			);
		}
	}
}
