package se.datasektionen.mc.metacraft_core.item.items;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.extensions.BlockEntityExtensions;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.util.helper.PlayerInventoryHelper;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public class Wrench extends Item implements PolymerItem {

	private static final ParticleEffect RED_DUST = new DustParticleEffect(
			ColorHelper.fromFloats(1, 1, 0, 0), 1
	);

	public Wrench(Settings settings) {
		super(settings);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
		return Items.BRICK;
	}


	private Text getBlockText(boolean newState) {
		if (newState) {
			return Text.translatableWithFallback(
					"item.metacraft.wrench.block.on",
					"The block is now movable by pistons"
			).styled(style -> style.withFormatting(Formatting.GREEN));
		} else {
			return Text.translatableWithFallback(
					"item.metacraft.wrench.block.off",
					"The block is no longer movable by pistons"
			).styled(style -> style.withFormatting(Formatting.RED));
		}
	}

	private Text getPlayerText(boolean newState) {
		if (newState) {
			return Text.translatableWithFallback(
					"item.metacraft.wrench.player.on",
					"All block entities you place will now be movable by pistons"
			).styled(style -> style.withFormatting(Formatting.GREEN));
		} else {
			return Text.translatableWithFallback(
					"item.metacraft.wrench.player.off",
					"All block entities you place will no longer be movable by pistons"
			).styled(style -> style.withFormatting(Formatting.RED));
		}
	}

	private Text getTooltipText(boolean currentState) {
		Text state;
		if (currentState) {
			state = Text.translatableWithFallback(
					"item.metacraft.wrench.tooltip.on",
					"Movable"
			).styled(style -> style.withFormatting(Formatting.GREEN));
		} else {
			state = Text.translatableWithFallback(
					"item.metacraft.wrench.tooltip.off",
					"Not Movable"
			).styled(style -> style.withFormatting(Formatting.RED));
		}
		return Text.translatableWithFallback(
				"item.metacraft.wrench.tooltip",
				"Default: " + state.getString(),
				state
		);
	}

	private void spawnParticlesForPlayer(
			ServerWorld world, PlayerEntity player, ParticleEffect particle,
			double x, double y, double z, int count,
			float xOffset, float yOffset, float zOffset, float speed
	) {
		for (var p : world.getPlayers()) {
			world.sendToPlayerIfNearby(
					p, p == player, x, y, z,
					new ParticleS2CPacket(
							particle, p == player, p == player,
							x, y, z, xOffset, yOffset, zOffset,
							speed, count
					)
			);
		}
	}

	public static boolean canUse(World world, BlockPos pos) {
		return world.getBlockEntity(pos) != null;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld() instanceof ServerWorld world) {
			var entity = context.getWorld().getBlockEntity(context.getBlockPos());
			if (entity != null) {
				boolean newState = !((BlockEntityExtensions) entity).metacraft_core$isMovable();
				((BlockEntityExtensions) entity).metacraft_core$setMovable(newState);
				context.getPlayer().sendMessage(
						getBlockText(newState), true
				);
				var pos = Vec3d.ofCenter(context.getBlockPos());
				if (newState) {
					spawnParticlesForPlayer(
							world, context.getPlayer(),
							ParticleTypes.HAPPY_VILLAGER,
							pos.getX(), pos.getY(), pos.getZ(), 50,
							0.45f, 0.45f, 0.45f, 1
					);
				} else {
					spawnParticlesForPlayer(
							world, context.getPlayer(),
							RED_DUST,
							pos.getX(), pos.getY(), pos.getZ(), 50,
							0.4f, 0.4f, 0.4f, 0.1f
					);
				}
				world.playSound(
						null,
						pos.getX(), pos.getY(), pos.getZ(),
						newState ? SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON : SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF,
						SoundCategory.BLOCKS, 1, 1
				);
				context.getPlayer().getItemCooldownManager().set(context.getStack(), 1); //Sneak-clicking on blocks should not change default state.
				return ActionResult.SUCCESS_SERVER;
			}
		}
		return super.useOnBlock(context);
	}



	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (user.isSneaking() && world instanceof ServerWorld) {
			var newState = !((ServerPlayerEntityExtensions) user).metacraft_core$areBlocksPistonMovable();
			((ServerPlayerEntityExtensions) user).metacraft_core$setBlocksPistonMovable(newState);
			user.sendMessage(
					getPlayerText(newState), true
			);
			world.playSound(
					null,
					user.getX(), user.getY(), user.getZ(),
					newState ? SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON : SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF,
					SoundCategory.PLAYERS, 1, 1
			);
			PlayerInventoryHelper.syncHandStack(user, hand); //Update tooltip.
			return ActionResult.SUCCESS_SERVER;
		}
		return super.use(world, user, hand);
	}

	private static final int SCAN_RADIUS = 10;

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected); //selected is false in offhand.
		if (entity instanceof ServerPlayerEntity player && entity.isSneaking() && PlayerInventoryHelper.isSelected(player, slot)) {
			int minX = ChunkSectionPos.getSectionCoord(player.getBlockX() - SCAN_RADIUS);
			int minZ = ChunkSectionPos.getSectionCoord(player.getBlockZ() - SCAN_RADIUS);
			int maxX = ChunkSectionPos.getSectionCoord(player.getBlockX() + SCAN_RADIUS);
			int maxZ = ChunkSectionPos.getSectionCoord(player.getBlockZ() + SCAN_RADIUS);

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					for (var pos : world.getChunk(x, z).getBlockEntityPositions()) {
						if (pos.isWithinDistance(player.getPos(), SCAN_RADIUS)) {
							boolean movable = ((BlockEntityExtensions) world.getBlockEntity(pos)).metacraft_core$isMovable();
							if (movable) {
								player.getServerWorld().spawnParticles(
										player, ParticleTypes.HAPPY_VILLAGER, true, true,
										pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5,
										0.45f, 0.45f, 0.45f, 1
								);
							} else {
								player.getServerWorld().spawnParticles(
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
	public void modifyClientTooltip(List<Text> tooltip, ItemStack stack, PacketContext context) {
		var player = context.getPlayer();
		if (player != null) {
			tooltip.add(
					getTooltipText(
							((ServerPlayerEntityExtensions) player).metacraft_core$areBlocksPistonMovable()
					)
			);
		}
	}
}
