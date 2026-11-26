package se.metacraft.portable_jukebox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemContainerContents;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

public record PortableJukeboxConfig(
	DefaultUpgrades defaultUpgrades
) {

	public static final MapCodec<PortableJukeboxConfig> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			CommentCodec.comment(
				DefaultUpgrades.CODEC.fieldOf("default_upgrades"),
				Component.translatable("config.portable_jukebox.default_upgrades")
			).forGetter(PortableJukeboxConfig::defaultUpgrades)
		).apply(instance, PortableJukeboxConfig::new)
	);

	private static final ConfigContainer<PortableJukeboxConfig> CONTAINER = ConfigContainer.Builder.create(
		CODEC, () -> new PortableJukeboxConfig(
			new DefaultUpgrades(9, false, false, false, false)
		)
	).resaveOnServerStart().build("portable-jukebox");

	public static PortableJukeboxConfig getConfig() {
		return CONTAINER.get();
	}

	public record DefaultUpgrades(
		int storage,
		boolean pitch,
		boolean volume,
		boolean shuffle,
		boolean playlist
	) {
		public static final MapCodec<DefaultUpgrades> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				CommentCodec.comment(
					Codec.intRange(0, ItemContainerContents.MAX_SIZE).fieldOf("storage"),
					Component.translatable("config.portable_jukebox.default_upgrades.storage")
				).forGetter(DefaultUpgrades::storage),
				CommentCodec.comment(
					Codec.BOOL.fieldOf("pitch"),
					Component.translatable("config.portable_jukebox.default_upgrades.pitch")
				).forGetter(DefaultUpgrades::pitch),
				CommentCodec.comment(
					Codec.BOOL.fieldOf("volume"),
					Component.translatable("config.portable_jukebox.default_upgrades.volume")
				).forGetter(DefaultUpgrades::volume),
				CommentCodec.comment(
					Codec.BOOL.fieldOf("shuffle"),
					Component.translatable("config.portable_jukebox.default_upgrades.shuffle")
				).forGetter(DefaultUpgrades::shuffle),
				CommentCodec.comment(
					Codec.BOOL.fieldOf("playlist"),
					Component.translatable("config.portable_jukebox.default_upgrades.playlist")
				).forGetter(DefaultUpgrades::playlist)
			).apply(instance, DefaultUpgrades::new)
		);
	}

}
