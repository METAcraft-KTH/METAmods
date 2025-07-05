package nu.metacraft.moderation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import nu.metacraft.lib.util.ExtraCodecs;
import nu.metacraft.moderation.moderator_mode.ModeratorModeDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ModerationData extends PersistentState {

	protected Map<String, ModeratorModeDefinition> definitions = new HashMap<>();

	public static ModerationData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	private static final Codec<ModerationData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ExtraCodecs.createListSerializedMap(
							Codec.STRING.fieldOf(ModeratorModeDefinition.NAME), ModeratorModeDefinition.CODEC,
							HashMap::new
					).fieldOf("Definitions").forGetter(d -> d.definitions)
			).apply(instance, ModerationData::fromData)
	);

	private static final PersistentStateType<ModerationData> TYPE = new PersistentStateType<>(
			"metacraft-moderation", ModerationData::createNew, CODEC, null
	);

	public Optional<ModeratorModeDefinition> getDefinition(String name) {
		return Optional.ofNullable(definitions.get(name));
	}

	public Stream<String> getValidDefinitionNamesFor(ServerPlayerEntity player) {
		return definitions.keySet().stream().filter(def -> {
			return PlayerModerationState.canEnterModerationMode(player, def);
		});
	}

	public void addModeratorDef(ModeratorModeDefinition definition) {
		definition.setSave(this::markDirty);
		definitions.put(definition.getName(), definition);
		markDirty();
	}

	public void removeModeratorDef(String name) {
		definitions.remove(name);
		markDirty();
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
				def -> def.setSave(this::markDirty)
		);
	}

}
