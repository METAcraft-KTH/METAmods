package metacraft.moredyes.sheep;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import metacraft.moredyes.content.Family;
import metacraft.moredyes.content.ModContent;
import metacraft.moredyes.content.ModDyeItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A sheep in one of our colours. The colour lives in a persistent Fabric attachment; the vanilla
 * colour field is forced to WHITE while it is set so every vanilla path (data, spawn eggs, jeb_)
 * stays sane. A vanilla dye, or anything else calling {@code setColor}, clears the attachment.
 *
 * Rendering: {@link SheepOverlay} tells vanilla clients the sheep is sheared, and
 * {@link SheepWoolRig} draws the wool in our colour with display entities.
 */
public final class SheepColors {
	public static final AttachmentType<String> COLOR = AttachmentRegistry.<String>builder()
			.persistent(Codec.STRING)
			.buildAndRegister(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "sheep_color"));

	/** Set while we call setColor(WHITE) ourselves, so the reset hook ignores it. */
	static boolean settingOurselves;

	private static final Map<Sheep, SheepWoolRig> RIGS = new WeakHashMap<>();

	private SheepColors() {}

	public static @Nullable ModColor get(Sheep sheep) {
		String id = sheep.getAttached(COLOR);
		if (id == null) return null;
		ModColor color = ModColors.getOrNull(id);
		if (color == null) {
			// A colour removed from colors.json against the rules. Loud, then recover to plain white.
			MoreDyes.LOGGER.error("[{}] sheep {} has unknown colour '{}'; clearing it", MoreDyes.MOD_ID, sheep.getUUID(), id);
			sheep.removeAttached(COLOR);
		}
		return color;
	}

	public static void set(Sheep sheep, ModColor color) {
		settingOurselves = true;
		try {
			sheep.setColor(DyeColor.WHITE);
		} finally {
			settingOurselves = false;
		}
		sheep.setAttached(COLOR, color.id());
		resendWoolData(sheep);
		ensureRig(sheep);
	}

	/** Called from the setColor mixin: vanilla changed the colour, so ours no longer applies. */
	public static void onVanillaColorSet(Sheep sheep) {
		if (settingOurselves || sheep.level().isClientSide()) return;
		if (sheep.removeAttached(COLOR) != null) {
			resendWoolData(sheep);
			SheepWoolRig rig = RIGS.remove(sheep);
			if (rig != null) rig.destroy();
		}
	}

	/**
	 * Forces the wool byte back through the entity tracker so {@link SheepOverlay} gets to rewrite
	 * it: toggling sheared marks the entry dirty even though the final value is unchanged.
	 */
	private static void resendWoolData(Sheep sheep) {
		boolean sheared = sheep.isSheared();
		sheep.setSheared(!sheared);
		sheep.setSheared(sheared);
		boolean invisible = sheep.isInvisible();
		sheep.setInvisible(!invisible);
		sheep.setInvisible(invisible);
	}

	public static void ensureRig(Sheep sheep) {
		if (sheep.level().isClientSide() || get(sheep) == null) return;
		RIGS.computeIfAbsent(sheep, s -> {
			MoreDyes.LOGGER.debug("[{}] wool rig attached to sheep {}", MoreDyes.MOD_ID, s.getUUID());
			return SheepWoolRig.attach(s);
		});
	}

	public static ResourceKey<LootTable> deathLootTable(ModColor color, boolean sheared) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID,
				"entities/sheep/" + color.id() + (sheared ? "_sheared" : "")));
	}

	public static void init() {
		// NOT registerOverlay: that also marks the SHEEP type as a Polymer object and hides it from the
		// registry sync, shifting every later entity type id on vanilla clients (decoded as the wrong
		// entity, "Network Protocol Error"). The constructor hook alone is what we want.
		PolymerEntityUtils.registerPolymerEntityConstructor(EntityTypes.SHEEP, SheepOverlay::new);

		// Our dye on a sheep. Not a DyeItem, so vanilla's mobInteract ignores it; we handle it here.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(entity instanceof Sheep sheep)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof ModDyeItem dye)) return InteractionResult.PASS;
			if (!sheep.isAlive() || sheep.isSheared() || get(sheep) == dye.color()) return InteractionResult.PASS;
			set(sheep, dye.color());
			level.playSound(null, sheep, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (!player.getAbilities().instabuild) stack.shrink(1);
			return InteractionResult.SUCCESS;
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Sheep sheep)) return;
			// A saved sheep whose vanilla colour is no longer white was recoloured by something that
			// bypassed setColor (NBT edits reload attachments after our hook ran): vanilla wins.
			if (sheep.getAttached(COLOR) != null && sheep.getColor() != DyeColor.WHITE) {
				MoreDyes.LOGGER.info("[{}] sheep {} has vanilla colour {}; dropping More Dyes colour",
						MoreDyes.MOD_ID, sheep.getUUID(), sheep.getColor());
				sheep.removeAttached(COLOR);
				return;
			}
			ensureRig(sheep);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Sheep sheep) {
				SheepWoolRig rig = RIGS.remove(sheep);
				if (rig != null) rig.destroy();
			}
		});
	}

	/** The wool block for a colour, for drops. */
	public static ItemStack woolStack(ModColor color, int count) {
		return new ItemStack(ModContent.block(color, Family.WOOL), count);
	}
}
