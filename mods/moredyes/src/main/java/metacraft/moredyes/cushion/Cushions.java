package metacraft.moredyes.cushion;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Cushions in our colours. A cushion is an entity whose colour is a {@link net.minecraft.world.item.DyeColor}
 * the client picks a texture by, so ours is a vanilla (white) cushion carrying our colour, which
 * clients are sent as an interaction box the size of a cushion (to click and sit on; see
 * {@link CushionOverlay}) with the cushion drawn on it as a display model in our colour.
 *
 * <p>The colour rides from item to entity and back: our cushion item carries {@link #ITEM_COLOR},
 * which the placed cushion reads with its other item components ({@code CushionMixin}) into
 * {@link #COLOR}; broken, it drops our item.
 */
public final class Cushions {
	/** Our colour on a cushion item (server only). */
	public static final DataComponentType<String> ITEM_COLOR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "cushion_color"),
			DataComponentType.<String>builder().persistent(Codec.STRING).build());

	/** Our colour on a placed cushion. */
	public static final AttachmentType<String> COLOR = AttachmentRegistry.<String>builder()
			.persistent(Codec.STRING)
			.buildAndRegister(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "cushion_color"));

	private Cushions() {}

	public static void init() {
		PolymerComponent.registerDataComponent(ITEM_COLOR);
		PolymerEntityUtils.registerPolymerEntityConstructor(EntityTypes.CUSHION, CushionOverlay::new);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Cushion cushion && colour(cushion) != null) draw(cushion);
		});
	}

	public static @Nullable ModColor colour(Cushion cushion) {
		String id = cushion.getAttached(COLOR);
		return id == null ? null : ModColors.getOrNull(id);
	}

	/** The display model of a cushion in its colour: vanilla's 16×4×16, sitting on the entity's position. */
	private static void draw(Cushion cushion) {
		ModColor color = colour(cushion);
		ItemStack model = new ItemStack(Items.PAPER);
		model.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "cushion/" + color.id()));
		ItemDisplayElement element = new ItemDisplayElement(model);
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		// the model's 0..4 px rows sit at -0.5..-0.25 around its centre; lift it onto the ground
		element.setTranslation(new Vector3f(0, 0.5f, 0));
		element.setYaw(cushion.getYRot());
		ElementHolder holder = new ElementHolder();
		holder.addElement(element);
		EntityAttachment.ofTicking(holder, cushion);
	}
}
