package nu.metacraft.player_specific_scoreboards.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.ResolutionContext;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record PlayerScoreboard(
		Component title,
		Optional<NumberFormat> numberFormat, List<Entry> entries
) {

	public static final String SCOREBOARD_ID = "#metacraft:player_sidebar";

	public static final Codec<PlayerScoreboard> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ComponentSerialization.CODEC.fieldOf("title").forGetter(PlayerScoreboard::title),
					NumberFormatTypes.CODEC.optionalFieldOf("format").forGetter(PlayerScoreboard::numberFormat),
					Entry.CODEC.listOf().fieldOf("entries").forGetter(PlayerScoreboard::entries)
			).apply(instance, PlayerScoreboard::new)
	);

	public PlayerScoreboard resolve(ResolutionContext ctx) throws CommandSyntaxException {
		var newTitle = ComponentUtils.resolve(
				ctx, title
		);
		List<Entry> newEntries = new ArrayList<>();
		for (var entry : entries) {
			newEntries.add(entry.resolve(ctx));
		}
		return new PlayerScoreboard(newTitle, numberFormat, newEntries);
	}

	public record Entry(Component name, Optional<String> ownerName, int value, Optional<NumberFormat> format) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						ComponentSerialization.CODEC.fieldOf("name").forGetter(Entry::name),
						Codec.STRING.optionalFieldOf("owner_name").forGetter(Entry::ownerName),
						Codec.INT.fieldOf("value").forGetter(Entry::value),
						NumberFormatTypes.CODEC.optionalFieldOf("format").forGetter(Entry::format)
				).apply(instance, Entry::new)
		);

		public String getOwnerName() {
			return ownerName.orElseGet(name::getString);
		}

		public ClientboundSetScorePacket createSetValuePacket() {
			return new ClientboundSetScorePacket(getOwnerName(), SCOREBOARD_ID, value, Optional.of(name), format);
		}

		public Entry resolve(ResolutionContext ctx) throws CommandSyntaxException {
			var newName = ComponentUtils.resolve(ctx, name);
			return new Entry(newName, ownerName, value, format);
		}
	}

	public Objective createObjective() {
		return new Objective(
				FakeScoreboard.getInstance(), SCOREBOARD_ID,
				ObjectiveCriteria.DUMMY, title,
				ObjectiveCriteria.RenderType.INTEGER, false, numberFormat.orElse(null)
		);
	}

}
