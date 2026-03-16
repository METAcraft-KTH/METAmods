package nu.metacraft.moderation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.moderation.moderator_mode.ModeratorModeDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ModerationData extends SavedData {

	protected Map<String, ModeratorModeDefinition> definitions = new HashMap<>();

	public static ModerationData getInstance(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static final Codec<Map<String, ModeratorModeDefinition>> DEF_MAP_CODEC = METACodecs.createListSerializedMap(
			METACodecs.withAlternative(
					Codec.STRING.fieldOf(ModeratorModeDefinition.NAME),
					Codec.STRING.fieldOf("Name")
			),
			ModeratorModeDefinition.CODEC,
			HashMap::new
	);

	private static final Codec<ModerationData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					METACodecs.withAlternative(
							DEF_MAP_CODEC.fieldOf("definitions"),
							DEF_MAP_CODEC.fieldOf("Definitions")
					).forGetter(d -> d.definitions)
			).apply(instance, ModerationData::fromData)
	);

	private static final SavedDataType<ModerationData> TYPE = new SavedDataType<>(
			"metacraft-moderation", ModerationData::createNew, CODEC, null
	);

	public Optional<ModeratorModeDefinition> getDefinition(String name) {
		return Optional.ofNullable(definitions.get(name));
	}

	public Stream<String> getValidDefinitionNamesFor(ServerPlayer player) {
		return definitions.keySet().stream().filter(def -> {
			return PlayerModerationState.canEnterModerationMode(player, def);
		});
	}

	public void addModeratorDef(ModeratorModeDefinition definition) {
		definition.setSave(this::setDirty);
		definitions.put(definition.getName(), definition);
		setDirty();
	}

	public void removeModeratorDef(String name) {
		definitions.remove(name);
		setDirty();
	}

	private static ModerationData createNew() {
		METAcraftModeration.LOGGER.info("No previous state found, setting default values");
		return new ModerationData(Map.of());
	}

	private static ModerationData fromData(Map<String, ModeratorModeDefinition> definitions) {
		METAcraftModeration.LOGGER.info("Previous state found, loading values");
		return new ModerationData(definitions);
	}

	protected ModerationData(Map<String, ModeratorModeDefinition> definitions) {
		this.definitions.putAll(definitions);
		this.definitions.values().forEach(
				def -> def.setSave(this::setDirty)
		);
	}

}
