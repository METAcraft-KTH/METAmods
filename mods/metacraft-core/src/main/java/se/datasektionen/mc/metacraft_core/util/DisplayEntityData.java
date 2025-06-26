package se.datasektionen.mc.metacraft_core.util;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.AffineTransformation;

public class DisplayEntityData {

	protected int interpolationDuration = 1;
	protected int startInterpolation = -1;
	protected int teleportDuration = 1;
	protected AffineTransformation transformation = AffineTransformation.identity();

	protected DisplayEntity.BillboardMode billboardMode = DisplayEntity.BillboardMode.FIXED;
	protected Brightness brightness = null;
	protected float viewRange = 1;
	protected float shadowRadius = 0;
	protected float shadowStrength = 1;
	protected float width = 0;
	protected float height = 0;
	protected int glowColourOverride = -1;
	protected boolean glowing;

	public int getInterpolationDuration() {
		return interpolationDuration;
	}

	public int getStartInterpolation() {
		return startInterpolation;
	}

	public int getTeleportDuration() {
		return teleportDuration;
	}

	public AffineTransformation getTransformation() {
		return transformation;
	}

	public void applySettings(DisplayElement element) {
		element.setInterpolationDuration(interpolationDuration);
		element.setTeleportDuration(teleportDuration);
		element.setStartInterpolation(startInterpolation);
		applySettingsNoInterpolation(element);
	}

	public void applySettingsNoInterpolation(DisplayElement element) {
		element.setBillboardMode(billboardMode);
		element.setBrightness(brightness);
		element.setViewRange(viewRange);
		element.setShadowRadius(shadowRadius);
		element.setShadowStrength(shadowStrength);
		element.setDisplayWidth(width);
		element.setDisplayHeight(height);
		element.setGlowColorOverride(glowColourOverride);
		element.setGlowing(glowing);
	}

	public void load(ReadView nbt, Entity entity) {
		startInterpolation = nbt.getInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
		interpolationDuration = nbt.getInt(DisplayEntity.INTERPOLATION_DURATION_KEY, 0);
		teleportDuration = nbt.getInt(DisplayEntity.TELEPORT_DURATION_KEY, 0);
		transformation = nbt.read(DisplayEntity.TRANSFORMATION_NBT_KEY, AffineTransformation.ANY_CODEC).orElse(AffineTransformation.identity());
		billboardMode = nbt.read(DisplayEntity.BILLBOARD_NBT_KEY, DisplayEntity.BillboardMode.CODEC).orElse(DisplayEntity.BillboardMode.FIXED);
		brightness = nbt.read(DisplayEntity.BRIGHTNESS_NBT_KEY, Brightness.CODEC).orElse(null);
		viewRange = nbt.getFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, 1);
		shadowRadius = nbt.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, 0);
		shadowStrength = nbt.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, 1);
		width = nbt.getFloat(DisplayEntity.WIDTH_NBT_KEY, 0);
		height = nbt.getFloat(DisplayEntity.HEIGHT_NBT_KEY, 0);
		glowColourOverride = nbt.getInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, -1);
		glowing = entity.isGlowing();
	}

	public void save(WriteView nbt, Entity entity) {
		nbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, startInterpolation);
		nbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, interpolationDuration);
		nbt.putInt(DisplayEntity.TELEPORT_DURATION_KEY, teleportDuration);
		nbt.put(DisplayEntity.BILLBOARD_NBT_KEY, DisplayEntity.BillboardMode.CODEC, billboardMode);
		nbt.putNullable(DisplayEntity.BRIGHTNESS_NBT_KEY, Brightness.CODEC, brightness);
		nbt.putFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, viewRange);
		nbt.putFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, shadowRadius);
		nbt.putFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, shadowStrength);
		nbt.putFloat(DisplayEntity.WIDTH_NBT_KEY, width);
		nbt.putFloat(DisplayEntity.HEIGHT_NBT_KEY, height);
		nbt.putInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, glowColourOverride);
		nbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, AffineTransformation.ANY_CODEC, transformation);
	}


	public static class Item extends DisplayEntityData {
		protected static final String ITEM = "item";
		protected static final String ITEM_DISPLAY = "item_display";

		protected ItemStack stack = ItemStack.EMPTY;
		protected ItemDisplayContext itemModel = ItemDisplayContext.NONE;

		public ItemStack getItem() {
			return stack;
		}

		public void setItem(ItemStack item) {
			this.stack = item;
		}

		public void applyItemSettings(ItemDisplayElement element) {
			element.setItem(stack);
			element.setItemDisplayContext(itemModel);
		}

		@Override
		public void load(ReadView nbt, Entity entity) {
			super.load(nbt, entity);
			stack = nbt.read(ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
			itemModel = nbt.read(ITEM_DISPLAY, ItemDisplayContext.CODEC).orElse(ItemDisplayContext.NONE);
		}

		@Override
		public void save(WriteView nbt, Entity entity) {
			super.save(nbt, entity);
			if (!stack.isEmpty()) {
				nbt.put(ITEM, ItemStack.CODEC, stack);
			}
			nbt.put(ITEM_DISPLAY, ItemDisplayContext.CODEC, itemModel);
		}
	}

	public static class Block extends DisplayEntityData {

		private BlockState state = Blocks.AIR.getDefaultState();

		public BlockState getBlockState() {
			return state;
		}

		public void setBlockState(BlockState state) {
			this.state = state;
		}

		public void applyBlockSettings(BlockDisplayElement element) {
			element.setBlockState(state);
		}

		@Override
		public void load(ReadView nbt, Entity entity) {
			super.load(nbt, entity);
			state = nbt.read(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY, BlockState.CODEC).orElse(
					Blocks.AIR.getDefaultState()
			);
		}

		@Override
		public void save(WriteView nbt, Entity entity) {
			super.save(nbt, entity);
			nbt.put(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY, BlockState.CODEC, state);
		}
	}

}
