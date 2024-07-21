package se.datasektionen.mc.metacraft_moderation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_moderation.moderator_mode.ModeratorModeDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ModerationData extends PersistentState {

	private static final String stateKey = "metacraft-moderation";
	private static final String DEFINITIONS = "Definitions";

	protected Map<String, ModeratorModeDefinition> definitions = new HashMap<>();

	public static ModerationData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), stateKey);
	}

	private static PersistentState.Type<ModerationData> getType(MinecraftServer server) {
		return new Type<>(
				() -> createNew(server), (nbt, lookup) -> fromNbt(server, nbt, lookup), null
		);
	}

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

	private static ModerationData createNew(MinecraftServer server) {
		METAcraftModeration.LOGGER.info("No previous state found, setting default values");
		return new ModerationData(server);
	}

	private static ModerationData fromNbt(MinecraftServer server, NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		METAcraftModeration.LOGGER.info("Previous state found, loading values");
		ModerationData settings = new ModerationData(server);
		settings.readNbt(tag, lookup);
		return settings;
	}

	protected final MinecraftServer server;

	protected ModerationData(MinecraftServer server) {
		this.server = server;
	}

	public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		this.definitions.clear();
		NbtList definitions = tag.getList(DEFINITIONS, NbtElement.COMPOUND_TYPE);
		for (var defNBT : definitions) {
			var def = new ModeratorModeDefinition("");
			def.setSave(this::markDirty);
			def.fromNBT((NbtCompound) defNBT);
			this.definitions.put(def.getName(), def);
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList definitions = new NbtList();
		this.definitions.values().forEach(def -> definitions.add(def.toNBT()));
		tag.put(DEFINITIONS, definitions);

		return tag;
	}

}
