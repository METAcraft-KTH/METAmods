package se.metacraft.east_vs_west;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The three teams. {@link #id} is the one key used everywhere: the config, the commands and the database's
 * {@code team} columns, which the website reads. "South" is only the rest team's flavour text.
 *
 * <p>These are the event's own teams, kept in the database: nobody is put in a vanilla scoreboard team.
 */
public enum Team implements StringRepresentable {
	EAST("east", ChatFormatting.RED),
	WEST("west", ChatFormatting.BLUE),
	REST("rest", ChatFormatting.GREEN);

	public static final Codec<Team> CODEC = StringRepresentable.fromEnum(Team::values);

	private final String id;
	private final ChatFormatting color;

	Team(String id, ChatFormatting color) {
		this.id = id;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public ChatFormatting color() {
		return color;
	}

	/** The team's configured name in its colour. */
	public MutableComponent displayName() {
		return Component.literal(EvwConfig.get().team(this).displayName()).withStyle(color);
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	/** The team with this key, or null: for a value read from the database. */
	@Nullable
	public static Team byId(@Nullable String id) {
		for (var team : values()) {
			if (team.id.equals(id)) {
				return team;
			}
		}
		return null;
	}
}
