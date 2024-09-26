package se.datasektionen.mc.metacraft_core.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class NoArmorDamageData extends PersistentState {
	private static final String KEY = METAcraftCore.NAMESPACE + "-no-armor-damage";
	public static final String NBT_KEY = "disableArmorDamage";

	private boolean disableArmorDamage = false;

	public NoArmorDamageData(MinecraftServer server) {
	}

	private static PersistentState.Type<NoArmorDamageData> getType(MinecraftServer server) {
		return new Type<>(() -> new NoArmorDamageData(server), (nbt, lookup) -> fromNBT(server, nbt), null);
	}

	private static NoArmorDamageData fromNBT(MinecraftServer server, NbtCompound nbt) {
		var data = new NoArmorDamageData(server);
		data.readNBT(nbt);
		return data;
	}

	public static NoArmorDamageData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), KEY);
	}
	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putBoolean(NBT_KEY, this.disableArmorDamage);
		return nbt;
	}

	private void readNBT(NbtCompound nbt) {
		this.disableArmorDamage = nbt.getBoolean(NBT_KEY);
	}

	public boolean isDisableArmorDamage() {
		return disableArmorDamage;
	}

	public void setDisableArmorDamage(boolean disableArmorDamage) {
		this.disableArmorDamage = disableArmorDamage;
		this.markDirty();
	}
}
