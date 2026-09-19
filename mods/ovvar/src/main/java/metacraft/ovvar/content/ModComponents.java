package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import metacraft.ovvar.Ovvar;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/** Mod-owned item components, server-side only: Polymer hides the types from vanilla clients and strips them from stacks. */
public final class ModComponents {
	private ModComponents() {}

	/** {@code spot.patch} placements sewn on the ovve, in sewing order (see Looks). Absent or empty = plain ovve. */
	public static final DataComponentType<SpotPlacements> PATCHES = register("patches",
			DataComponentType.<SpotPlacements>builder()
					.persistent(SpotPlacements.CODEC));

	/** One {@code field=patch} being previewed on an armour stand while a player aims a patch at it. Never sewn. */
	public static final DataComponentType<Placement> PREVIEW = register("preview",
			DataComponentType.<Placement>builder().persistent(Placement.CODEC));

	/** Whether the ovve's top is worn up (sleeves on) rather than hanging at the waist. Absent = down. */
	public static final DataComponentType<Boolean> TOP_UP = register("top_up",
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL));

	/**
	 * On an ovve worn by someone whose feet slot carries our second dye channel (a companion
	 * {@link OvveFeetItem}, or vanilla boots we wrap): the legs get three more instant patches.
	 * Never saved — the wearer's tick sets it.
	 */
	public static final DataComponentType<Boolean> FEET_CHANNEL = register("feet_channel",
			DataComponentType.<Boolean>builder().networkSynchronized(ByteBufCodecs.BOOL));

	/** On vanilla boots worn over an ovve: the wearer, whose legs' patches the boots' dye colour carries. Never saved. */
	public static final DataComponentType<UUID> WRAPPED = register("wrapped",
			DataComponentType.<UUID>builder().networkSynchronized(UUIDUtil.STREAM_CODEC));

	/** On a real chestplate worn over an ovve with the top up: the wearer, whose top the chestplate is shown carrying underneath. Never saved. */
	public static final DataComponentType<UUID> WRAPPED_TOP = register("wrapped_top",
			DataComponentType.<UUID>builder().networkSynchronized(UUIDUtil.STREAM_CODEC));

	/**
	 * On an ovve worn by an armour stand: its patches are shown as display entities on the stand
	 * ({@link metacraft.ovvar.sewing.StandDisplays}) and not drawn by the armour at all, so a
	 * sewing session needs neither the dye channels nor a pack. Cleared when a player picks it up.
	 */
	public static final DataComponentType<Boolean> ON_STAND = register("on_stand",
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

	/**
	 * Whose ovve this is: the design (the sewn patches) is filed under this player in the design
	 * store ({@link metacraft.ovvar.store.Designs}), and {@link #PATCHES} on an owned ovve is only a
	 * copy of it for drawing. Set by the first player whose inventory ticks it (config
	 * {@code designs.bind_on_pickup}) or by {@code /ovvar give}. Absent: an unowned ovve, whose
	 * patches are its own (stands set up by command).
	 */
	public static final DataComponentType<UUID> OWNER = register("owner",
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC));

	/**
	 * On the shears and the patch a stash session puts in the hotbar ({@link metacraft.ovvar.sewing.StashSession}):
	 * not the player's, cannot be dropped or moved, swept out when the session ends (or on join, after a crash).
	 */
	public static final DataComponentType<Boolean> SESSION = register("session",
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL));

	/** On a patch item stack used as a display entity: which piece of the art ({@link PatchPieces.Piece#key}), flat 1:1, instead of the inventory icon. Never saved. */
	public static final DataComponentType<String> FLAT = register("flat",
			DataComponentType.<String>builder().networkSynchronized(ByteBufCodecs.STRING_UTF8));

	private static <T> DataComponentType<T> register(String name, DataComponentType.Builder<T> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, name), builder.build());
	}

	public static void init() {
		// Registered types land in a synced registry; without this Fabric's registry sync kicks
		// vanilla clients ("requires Fabric Loader"). Polymer hides them and never sends them.
		PolymerComponent.registerDataComponent(PATCHES, PREVIEW, TOP_UP, FEET_CHANNEL, WRAPPED, WRAPPED_TOP, ON_STAND, OWNER, SESSION, FLAT);
	}
}
