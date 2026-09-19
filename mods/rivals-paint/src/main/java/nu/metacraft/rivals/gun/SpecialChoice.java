package nu.metacraft.rivals.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.rivals.Rivals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which special each player picked in {@link SpecialDialog}, by UUID — {@link WeaponChoice} for the
 * thing on F, and the same shape for the same reasons.
 *
 * <p>Saved data rather than a field in memory: a choice a player has to make again after every relog is
 * not a choice they made. One map for the whole server, since a player carries their special from
 * dimension to dimension, and stored as special <em>ids</em> rather than ordinals, so a special added
 * or reordered in the enum cannot hand everyone somebody else's bomb. An id nothing answers to any more
 * is dropped on read, which reads as "no choice yet" — the splat bomb.
 *
 * <p>Whether a player has ever picked matters as much as what they picked: {@link #get} empty is what
 * puts the picker in front of them after a weapon pick and at a match start, and it is why this is an
 * {@link Optional} rather than a map that answers {@link #DEFAULT} for everybody.
 */
public final class SpecialChoice extends SavedData {
	/** The special a player who never picked one throws: the one F has always thrown. */
	public static final Special DEFAULT = Special.SPLAT_BOMB;

	public static final Codec<SpecialChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).fieldOf("choices").forGetter(SpecialChoice::ids)
	).apply(instance, SpecialChoice::fromIds));

	private static final SavedDataType<SpecialChoice> TYPE = new SavedDataType<>(
			Rivals.id("rivals_special_choice"), SpecialChoice::new, CODEC, null);

	private final Map<UUID, Special> choices = new HashMap<>();

	public SpecialChoice() {}

	private static SpecialChoice fromIds(Map<UUID, String> ids) {
		SpecialChoice data = new SpecialChoice();
		ids.forEach((uuid, id) -> Special.byId(id).ifPresent(special -> data.choices.put(uuid, special)));
		return data;
	}

	private Map<UUID, String> ids() {
		Map<UUID, String> out = new HashMap<>();
		choices.forEach((uuid, special) -> out.put(uuid, special.commandId()));
		return out;
	}

	public static SpecialChoice of(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** What this player picked, if they ever have. */
	public Optional<Special> get(UUID player) {
		return Optional.ofNullable(choices.get(player));
	}

	public Optional<Special> get(Player player) {
		return get(player.getUUID());
	}

	/** What this player throws: their pick, or the splat bomb. */
	public Special orDefault(Player player) {
		return get(player).orElse(DEFAULT);
	}

	public void set(UUID player, Special special) {
		if (choices.put(player, special) != special) setDirty();
	}

	public void set(Player player, Special special) {
		set(player.getUUID(), special);
	}

	/** Forget a player's pick. For tests, and for a reset that should ask everybody again. */
	public void forget(UUID player) {
		if (choices.remove(player) != null) setDirty();
	}

	public int size() {
		return choices.size();
	}
}
