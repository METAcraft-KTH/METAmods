package se.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.nether_rifts.NetherRifts;
import org.jetbrains.annotations.Nullable;

/**
 * The Piglin Grenadier's sword on foot ({@link GhastBombardier}). On the server it is a golden sword in every way: built
 * as vanilla builds {@code Items.GOLDEN_SWORD} (same components: damage, speed, durability, enchantability, repair), and
 * in the item tags vanilla's is in (data/minecraft/tags/item/, and the c: convention tags' data/c/tags/item/). It never
 * drops and has no recipe.
 *
 * <p>Clients see a golden sword whose item model draws the artist's gloved right arm (the dark gauntlet with gold bands)
 * over the piglin's own, the sword still in its hand: {@code nether_rifts:grenadier_sword}, a composite of vanilla's
 * golden sword model and {@code nether_rifts:item/bombardier_arm}, each placed by its own display transforms. Through a
 * throw the held stack carries a {@link Throw} ({@link #THROW}, the server's only: it is never saved or sent), and
 * clients see the arm without the sword, holding the magma bomb till it lets go ({@code grenadier_sword_bomb}), then
 * empty ({@code grenadier_sword_thrown}). Changing it changes the stack, so the mob's equipment goes out again.
 */
public class GrenadierSword extends Item implements PolymerItem {

	/** Where a throw is: the bomb in the fist, or let go. */
	public enum Throw {
		BOMB("grenadier_sword_bomb"),
		THROWN("grenadier_sword_thrown");

		public final Identifier model;

		Throw(String model) {
			this.model = NetherRifts.getID(model);
		}
	}

	/**
	 * On the held sword through a throw. Never saved (no codec); Polymer keeps it off the stacks clients get (the stream
	 * codec is only there because a component must have one or the other).
	 */
	public static final DataComponentType<Throw> THROW = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			NetherRifts.getID("grenadier_throw"), DataComponentType.<Throw>builder()
					.networkSynchronized(ByteBufCodecs.VAR_INT.map(i -> Throw.values()[i], Throw::ordinal)).build());

	static {
		PolymerComponent.registerDataComponent(THROW);
	}

	/** The item model clients see the sword with, gloved arm and all. */
	public static final Identifier MODEL = NetherRifts.getID("grenadier_sword");

	public GrenadierSword(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.GOLDEN_SWORD;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		var thrown = stack.get(THROW);
		return thrown == null ? MODEL : thrown.model;
	}

	/** Sets where a throw is on the held sword (null: no throw, the sword shows again). Anything else is left alone. */
	public static void setThrow(ItemStack stack, @Nullable Throw at) {
		if (stack.getItem() instanceof GrenadierSword && stack.get(THROW) != at) {
			if (at == null) {
				stack.remove(THROW);
			} else {
				stack.set(THROW, at);
			}
		}
	}
}
