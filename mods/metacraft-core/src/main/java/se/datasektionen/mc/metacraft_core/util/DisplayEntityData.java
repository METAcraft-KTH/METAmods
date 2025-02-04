package se.datasektionen.mc.metacraft_core.util;

import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
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
		if (nbt.contains(DisplayEntity.START_INTERPOLATION_KEY)) {
			startInterpolation = nbt.getInt(DisplayEntity.START_INTERPOLATION_KEY);
		}
		if (nbt.contains(DisplayEntity.INTERPOLATION_DURATION_KEY)) {
			interpolationDuration = nbt.getInt(DisplayEntity.INTERPOLATION_DURATION_KEY);
		}
		if (nbt.contains(DisplayEntity.TELEPORT_DURATION_KEY)) {
			teleportDuration = nbt.getInt(DisplayEntity.TELEPORT_DURATION_KEY);
		}
		if (nbt.contains(DisplayEntity.TRANSFORMATION_NBT_KEY)) {
			var res = AffineTransformation.ANY_CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.TRANSFORMATION_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			res.ifPresent(affineTransformation -> this.transformation = affineTransformation);
		}
		if (nbt.contains(DisplayEntity.BILLBOARD_NBT_KEY)) {
			var b = DisplayEntity.BillboardMode.CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.BILLBOARD_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			b.ifPresent(mode -> billboardMode = mode);
		}
		if (nbt.contains(DisplayEntity.BRIGHTNESS_NBT_KEY)) {
			var b = Brightness.CODEC.parse(NbtOps.INSTANCE, nbt.get(DisplayEntity.BRIGHTNESS_NBT_KEY)).resultOrPartial(
					METAcraftCore.LOGGER::error
			);
			b.ifPresent(value -> brightness = value);
		} else {
			brightness = null;
		}
		if (nbt.contains(DisplayEntity.VIEW_RANGE_NBT_KEY)) {
			viewRange = nbt.getFloat(DisplayEntity.VIEW_RANGE_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.SHADOW_RADIUS_NBT_KEY)) {
			shadowRadius = nbt.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.SHADOW_STRENGTH_NBT_KEY)) {
			shadowStrength = nbt.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.WIDTH_NBT_KEY)) {
			width = nbt.getFloat(DisplayEntity.WIDTH_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.HEIGHT_NBT_KEY)) {
			height = nbt.getFloat(DisplayEntity.HEIGHT_NBT_KEY);
		}
		if (nbt.contains(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY)) {
			glowColourOverride = nbt.getInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY);
		}
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
		protected ModelTransformationMode itemModel = ModelTransformationMode.NONE;

		public ItemStack getItem() {
			return stack;
		}

		public void setItem(ItemStack item) {
			this.stack = item;
		}

		public void applyItemSettings(ItemDisplayElement element) {
			element.setItem(stack);
			element.setModelTransformation(itemModel);
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
				ModelTransformationMode.CODEC.parse(NbtOps.INSTANCE, nbt.get(ITEM_DISPLAY)).resultOrPartial(
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
			ModelTransformationMode.CODEC.encodeStart(NbtOps.INSTANCE, itemModel).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(m -> nbt.put(ITEM_DISPLAY, m));
		}
	}

}
