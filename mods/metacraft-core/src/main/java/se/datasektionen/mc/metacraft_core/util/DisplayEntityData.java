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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.AffineTransformation;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

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

	public void load(NbtCompound nbt, Entity entity) {
		startInterpolation = nbt.getInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
		interpolationDuration = nbt.getInt(DisplayEntity.INTERPOLATION_DURATION_KEY, 0);
		teleportDuration = nbt.getInt(DisplayEntity.TELEPORT_DURATION_KEY, 0);
		transformation = nbt.get(DisplayEntity.TRANSFORMATION_NBT_KEY, AffineTransformation.ANY_CODEC).orElse(AffineTransformation.identity());
		billboardMode = nbt.get(DisplayEntity.BILLBOARD_NBT_KEY, DisplayEntity.BillboardMode.CODEC).orElse(DisplayEntity.BillboardMode.FIXED);
		brightness = nbt.get(DisplayEntity.BRIGHTNESS_NBT_KEY, Brightness.CODEC).orElse(null);
		viewRange = nbt.getFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, 1);
		shadowRadius = nbt.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, 0);
		shadowStrength = nbt.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, 1);
		width = nbt.getFloat(DisplayEntity.WIDTH_NBT_KEY, 0);
		height = nbt.getFloat(DisplayEntity.HEIGHT_NBT_KEY, 0);
		glowColourOverride = nbt.getInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, -1);
		glowing = entity.isGlowing();
	}

	public void save(NbtCompound nbt, Entity entity) {
		nbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, startInterpolation);
		nbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, interpolationDuration);
		nbt.putInt(DisplayEntity.TELEPORT_DURATION_KEY, teleportDuration);
		DisplayEntity.BillboardMode.CODEC.encodeStart(NbtOps.INSTANCE, billboardMode).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(b -> nbt.put(DisplayEntity.BILLBOARD_NBT_KEY, b));
		if (brightness != null) {
			Brightness.CODEC.encodeStart(NbtOps.INSTANCE, brightness).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(brightness -> nbt.put(DisplayEntity.BRIGHTNESS_NBT_KEY, brightness));
		}
		nbt.putFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, viewRange);
		nbt.putFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, shadowRadius);
		nbt.putFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, shadowStrength);
		nbt.putFloat(DisplayEntity.WIDTH_NBT_KEY, width);
		nbt.putFloat(DisplayEntity.HEIGHT_NBT_KEY, height);
		nbt.putInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, glowColourOverride);
		AffineTransformation.ANY_CODEC.encodeStart(NbtOps.INSTANCE, transformation).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(t -> nbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, t));
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
		public void load(NbtCompound nbt, Entity entity) {
			super.load(nbt, entity);
			if (nbt.contains(ITEM)) {
				ItemStack.CODEC.parse(entity.getRegistryManager().getOps(NbtOps.INSTANCE), nbt.get(ITEM)).resultOrPartial(
						METAcraftCore.LOGGER::error
				).ifPresent(s -> stack = s);
			}
			if (nbt.contains(ITEM_DISPLAY)) {
				ItemDisplayContext.CODEC.parse(NbtOps.INSTANCE, nbt.get(ITEM_DISPLAY)).resultOrPartial(
						METAcraftCore.LOGGER::error
				).ifPresent(m -> itemModel = m);
			}
		}

		@Override
		public void save(NbtCompound nbt, Entity entity) {
			super.save(nbt, entity);
			if (!stack.isEmpty()) {
				ItemStack.CODEC.encodeStart(entity.getRegistryManager().getOps(NbtOps.INSTANCE), stack).resultOrPartial(
						METAcraftCore.LOGGER::error
				).ifPresent(s -> nbt.put(ITEM, s));
			}
			ItemDisplayContext.CODEC.encodeStart(NbtOps.INSTANCE, itemModel).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(m -> nbt.put(ITEM_DISPLAY, m));
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
		public void load(NbtCompound nbt, Entity entity) {
			super.load(nbt, entity);
			state = NbtHelper.toBlockState(
					entity.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK),
					nbt.getCompoundOrEmpty(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY)
			);
		}

		@Override
		public void save(NbtCompound nbt, Entity entity) {
			super.save(nbt, entity);
			nbt.put(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY, NbtHelper.fromBlockState(state));
		}
	}

}
