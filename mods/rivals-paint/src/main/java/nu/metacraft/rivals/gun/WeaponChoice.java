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
 * Which weapon each player picked in {@link WeaponDialog}, by UUID.
 *
 * <p>Saved data rather than a field in memory, because a choice a player has to make again after every
 * relog is not a choice they made: the match start hands out what they picked, and picking is a thing
 * that happens in the lobby, before a restart may well have happened. One map for the whole server (it
 * lives on {@code server.getDataStorage()}, not on a level), since a player carries their weapon from
 * dimension to dimension.
 *
 * <p>Stored as weapon <em>ids</em>, not ordinals: a weapon added or reordered in the enum would
 * otherwise hand everyone somebody else's gun. An id nothing answers to any more is dropped on read,
 * which reads as "no choice yet" — the shooter.
 */
public final class WeaponChoice extends SavedData {
	/** The weapon a player who never picked one is given. */
	public static final Weapon DEFAULT = Weapon.SHOOTER;

	public static final Codec<WeaponChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).fieldOf("choices").forGetter(WeaponChoice::ids)
	).apply(instance, WeaponChoice::fromIds));

	private static final SavedDataType<WeaponChoice> TYPE = new SavedDataType<>(
			Rivals.id("rivals_weapon_choice"), WeaponChoice::new, CODEC, null);

	private final Map<UUID, Weapon> choices = new HashMap<>();

	public WeaponChoice() {}

	private static WeaponChoice fromIds(Map<UUID, String> ids) {
		WeaponChoice data = new WeaponChoice();
		ids.forEach((uuid, id) -> Weapon.byId(id).ifPresent(weapon -> data.choices.put(uuid, weapon)));
		return data;
	}

	private Map<UUID, String> ids() {
		Map<UUID, String> out = new HashMap<>();
		choices.forEach((uuid, weapon) -> out.put(uuid, weapon.commandId()));
		return out;
	}

	public static WeaponChoice of(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** What this player picked, if they ever have. */
	public Optional<Weapon> get(UUID player) {
		return Optional.ofNullable(choices.get(player));
	}

	public Optional<Weapon> get(Player player) {
		return get(player.getUUID());
	}

	/** What this player should be handed: their pick, or the shooter. */
	public Weapon orDefault(Player player) {
		return get(player).orElse(DEFAULT);
	}

	public void set(UUID player, Weapon weapon) {
		if (choices.put(player, weapon) != weapon) setDirty();
	}

	public void set(Player player, Weapon weapon) {
		set(player.getUUID(), weapon);
	}

	/** Forget a player's pick. For tests, and for a reset that should hand out shooters again. */
	public void forget(UUID player) {
		if (choices.remove(player) != null) setDirty();
	}

	public int size() {
		return choices.size();
	}
}
