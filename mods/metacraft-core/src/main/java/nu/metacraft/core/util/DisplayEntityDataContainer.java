package nu.metacraft.core.util;

import com.mojang.math.Transformation;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DisplayEntityDataContainer {

	protected int interpolationDuration = 1;
	protected int startInterpolation = -1;
	protected int teleportDuration = 1;
	protected Transformation transformation = Transformation.IDENTITY;

	protected Display.BillboardConstraints billboardMode = Display.BillboardConstraints.FIXED;
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

	public Transformation getTransformation() {
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

	public void load(ValueInput nbt, Entity entity) {
		startInterpolation = nbt.getIntOr(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
		interpolationDuration = nbt.getIntOr(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, 0);
		teleportDuration = nbt.getIntOr(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 0);
		transformation = nbt.read(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC).orElse(Transformation.IDENTITY);
		billboardMode = nbt.read(Display.TAG_BILLBOARD, Display.BillboardConstraints.CODEC).orElse(Display.BillboardConstraints.FIXED);
		brightness = nbt.read(Display.TAG_BRIGHTNESS, Brightness.CODEC).orElse(null);
		viewRange = nbt.getFloatOr(Display.TAG_VIEW_RANGE, 1);
		shadowRadius = nbt.getFloatOr(Display.TAG_SHADOW_RADIUS, 0);
		shadowStrength = nbt.getFloatOr(Display.TAG_SHADOW_STRENGTH, 1);
		width = nbt.getFloatOr(Display.TAG_WIDTH, 0);
		height = nbt.getFloatOr(Display.TAG_HEIGHT, 0);
		glowColourOverride = nbt.getIntOr(Display.TAG_GLOW_COLOR_OVERRIDE, -1);
		glowing = entity.isCurrentlyGlowing();
	}

	public void save(ValueOutput nbt, Entity entity) {
		nbt.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, startInterpolation);
		nbt.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, interpolationDuration);
		nbt.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, teleportDuration);
		nbt.store(Display.TAG_BILLBOARD, Display.BillboardConstraints.CODEC, billboardMode);
		nbt.storeNullable(Display.TAG_BRIGHTNESS, Brightness.CODEC, brightness);
		nbt.putFloat(Display.TAG_VIEW_RANGE, viewRange);
		nbt.putFloat(Display.TAG_SHADOW_RADIUS, shadowRadius);
		nbt.putFloat(Display.TAG_SHADOW_STRENGTH, shadowStrength);
		nbt.putFloat(Display.TAG_WIDTH, width);
		nbt.putFloat(Display.TAG_HEIGHT, height);
		nbt.putInt(Display.TAG_GLOW_COLOR_OVERRIDE, glowColourOverride);
		nbt.store(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC, transformation);
	}


	public static class Item extends DisplayEntityDataContainer {
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
		public void load(ValueInput nbt, Entity entity) {
			super.load(nbt, entity);
			stack = nbt.read(ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
			itemModel = nbt.read(ITEM_DISPLAY, ItemDisplayContext.CODEC).orElse(ItemDisplayContext.NONE);
		}

		@Override
		public void save(ValueOutput nbt, Entity entity) {
			super.save(nbt, entity);
			if (!stack.isEmpty()) {
				nbt.store(ITEM, ItemStack.CODEC, stack);
			}
			nbt.store(ITEM_DISPLAY, ItemDisplayContext.CODEC, itemModel);
		}
	}

	public static class Block extends DisplayEntityDataContainer {

		private BlockState state = Blocks.AIR.defaultBlockState();

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
		public void load(ValueInput nbt, Entity entity) {
			super.load(nbt, entity);
			state = nbt.read(Display.BlockDisplay.TAG_BLOCK_STATE, BlockState.CODEC).orElse(
					Blocks.AIR.defaultBlockState()
			);
		}

		@Override
		public void save(ValueOutput nbt, Entity entity) {
			super.save(nbt, entity);
			nbt.store(Display.BlockDisplay.TAG_BLOCK_STATE, BlockState.CODEC, state);
		}
	}

}
