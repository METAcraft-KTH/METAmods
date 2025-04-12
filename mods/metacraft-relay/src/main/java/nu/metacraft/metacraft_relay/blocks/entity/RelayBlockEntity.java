package nu.metacraft.metacraft_relay.blocks.entity;

import com.mojang.serialization.DataResult;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.metacraft_relay.blocks.RelayBlockEntities;
import nu.metacraft.metacraft_relay.blocks.block.RelayBlock;
import nu.metacraft.metacraft_relay.items.RelayComponents;
import nu.metacraft.metacraft_relay.mixin.AccessorServerPlayerEntityRespawnPos;
import org.joml.Vector3f;
import org.pcollections.HashTreePSet;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.List;
import java.util.Set;

public class RelayBlockEntity extends BlockEntity {

	private final ElementHolder holder = new ElementHolder();
	private HolderAttachment attachment = null;
	private ItemDisplayElement display = null;


	protected RelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public RelayBlockEntity(BlockPos pos, BlockState state) {
		this(RelayBlockEntities.RELAY, pos, state);
	}

	@Override
	public void setCachedState(BlockState state) {
		super.setCachedState(state);
		updateDisplay();
	}

	private void removeAttachment() {
		if (attachment != null) {
			attachment.destroy();
			attachment = null;
		}
	}

	private void initAttachment(ServerWorld world) {
		if (display != null) {
			removeAttachment();
			attachment = ChunkAttachment.of(holder, world, pos);
		}
	}

	private void setDisplay(ItemStack item) {
		if (!(world instanceof ServerWorld world)) return;
		if (item.isEmpty()) return;
		if (display == null) {
			display = new ItemDisplayElement();
			holder.addElement(display);
			display.setBrightness(Brightness.FULL);
			display.setScale(new Vector3f(1.0004f));
		}
		display.setItem(item);
		if (attachment == null) {
			initAttachment(world);
		}
		display.tick();
	}

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		if (world.getServer() != null) {
			TaskScheduler.scheduleImmediately( //Set display after one tick, otherwise server freezes.
					world.getServer(),
					this::updateDisplay
			);
		}
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		removeAttachment();
	}

	private void updateDisplay() {
		setDisplay(getDisplayStack());
	}

	private ItemStack getDisplayStack() {
		var model = getComponents().get(RelayComponents.BLOCK_MODEL);
		if (model != null) {
			return new ItemStack(
					Items.BARRIER.getRegistryEntry(), 1,
					ComponentChanges.builder().add(
							DataComponentTypes.ITEM_MODEL, model
					).add(
							DataComponentTypes.CUSTOM_MODEL_DATA,
							new CustomModelDataComponent(
									List.of(),
									List.of(getCachedState().get(RelayBlock.CHARGED)),
									List.of(),
									List.of()
							)
					).build()
			);
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public void setComponents(ComponentMap components) {
		super.setComponents(components);
		updateDisplay();
	}

	public DataResult<TeleportTarget> getTarget() {
		var mappings = getComponents().get(RelayComponents.VALID_DIMENSIONS);
		Set<RegistryKey<World>> validTargets;
		if (mappings != null) {
			validTargets = mappings.getOrDefault(world.getRegistryKey(), HashTreePSet.empty());
		} else {
			validTargets = HashTreePSet.empty();
		}
		var target = getComponents().get(DataComponentTypes.LODESTONE_TRACKER);
		if (target != null && target.target().isPresent()) {
			if (!validTargets.contains(target.target().get().dimension())) {
				if (validTargets.size() == 1 && validTargets.contains(world.getRegistryKey())) {
					return DataResult.error(() -> "Target is in another dimension");
				}
				return DataResult.error(() -> "Target dimension is not reachable");
			}
			var dim = world.getServer().getWorld(target.target().get().dimension());
			if (dim == null) return DataResult.error(() -> "Targeted dimension does not exist");
			return target.forWorld(dim).target().map(
					t -> {
						var respawnPos = RespawnAnchorBlock.findRespawnPosition(
								EntityType.PLAYER, dim, t.pos()
						);
						return respawnPos.map(pos -> DataResult.success(
								new TeleportTarget(
										dim, pos, Vec3d.ZERO,
										AccessorServerPlayerEntityRespawnPos.callGetYaw(pos, t.pos()),
										0, TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET
								)
						)).orElseGet(() -> DataResult.error(
								() -> "Target lodestone is obstructed (" +
										target.target().map(p -> p.pos().toShortString() + ", " + p.dimension().getValue()).orElse("missingno") + ")"
						));
					}
			).orElse(DataResult.error(
					() -> "Target lodestone missing (" +
							target.target().map(t -> t.pos().toShortString() + ", " + t.dimension().getValue()).orElse("missingno") + ")"
			));
		}
		return DataResult.error(() -> "No Target");
	}

	public boolean shouldExplode() {
		var mappings = getComponents().get(RelayComponents.VALID_DIMENSIONS);
		return mappings == null || !mappings.containsKey(world.getRegistryKey());
	}
}
