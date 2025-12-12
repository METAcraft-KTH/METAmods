package nu.metacraft.pointsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import nu.metacraft.lib.config.container.ConfigContainer;

public record PointSystemConfig(
		Component universityScoreText,
		Component topPlayersText,
		String dbURL,
		String username,
		String password
) {

	public static final Codec<PointSystemConfig> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			ComponentSerialization.CODEC.fieldOf("university_score_text").forGetter(PointSystemConfig::universityScoreText),
			ComponentSerialization.CODEC.fieldOf("top_players_text").forGetter(PointSystemConfig::topPlayersText),
			Codec.STRING.fieldOf("db_url").forGetter(PointSystemConfig::dbURL),
			Codec.STRING.fieldOf("username").forGetter(PointSystemConfig::username),
			Codec.STRING.fieldOf("password").forGetter(PointSystemConfig::password)
		).apply(instance, PointSystemConfig::new)
	);

	private static final ConfigContainer<PointSystemConfig> CONFIG = ConfigContainer.Builder.create(
		CODEC, () -> new PointSystemConfig(
			Component.literal("University Score").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)),
			Component.literal("Top Players").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)),
			"jdbc:sqlite:point-system-data.db", "", ""
		)
	).build(FabricLoader.getInstance().getConfigDir().resolve("point-system-config.json"));

	public static PointSystemConfig getInstance() {
		return CONFIG.get();
	}

	public static void reload() {
		CONFIG.reload();
	}

}
