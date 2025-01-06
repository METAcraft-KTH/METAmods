package se.datasektionen.mc.cutscenes.util.cutscene_redirector;

import com.mojang.serialization.Lifecycle;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.ServerWorldProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DummySaveProperties implements SaveProperties {

	private static final DummySaveProperties INSTANCE = new DummySaveProperties();

	public static DummySaveProperties getInstance() {
		return INSTANCE;
	}

	private DummySaveProperties() {}

	@Override
	public FeatureSet getEnabledFeatures() {
		return DummyDynamicRegistryContainers.getFeatureSet();
	}

	@Override
	public DataConfiguration getDataConfiguration() {
		return null;
	}

	@Override
	public void updateLevelInfo(DataConfiguration dataConfiguration) {

	}

	@Override
	public boolean isModded() {
		return false;
	}

	@Override
	public Set<String> getServerBrands() {
		return Set.of();
	}

	@Override
	public Set<String> getRemovedFeatures() {
		return Set.of();
	}

	@Override
	public void addServerBrand(String brand, boolean modded) {

	}

	@Override
	public @Nullable NbtCompound getCustomBossEvents() {
		return null;
	}

	@Override
	public void setCustomBossEvents(@Nullable NbtCompound customBossEvents) {

	}

	@Override
	public ServerWorldProperties getMainWorldProperties() {
		return null;
	}

	@Override
	public LevelInfo getLevelInfo() {
		return null;
	}

	@Override
	public NbtCompound cloneWorldNbt(DynamicRegistryManager registryManager, @Nullable NbtCompound playerNbt) {
		return null;
	}

	@Override
	public boolean isHardcore() {
		return false;
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	public String getLevelName() {
		return "";
	}

	@Override
	public GameMode getGameMode() {
		return null;
	}

	@Override
	public void setGameMode(GameMode gameMode) {

	}

	@Override
	public boolean areCommandsAllowed() {
		return false;
	}

	@Override
	public Difficulty getDifficulty() {
		return null;
	}

	@Override
	public void setDifficulty(Difficulty difficulty) {

	}

	@Override
	public boolean isDifficultyLocked() {
		return false;
	}

	@Override
	public void setDifficultyLocked(boolean difficultyLocked) {

	}

	@Override
	public GameRules getGameRules() {
		return null;
	}

	@Override
	public @Nullable NbtCompound getPlayerData() {
		return null;
	}

	@Override
	public EnderDragonFight.Data getDragonFight() {
		return null;
	}

	@Override
	public void setDragonFight(EnderDragonFight.Data dragonFight) {

	}

	@Override
	public GeneratorOptions getGeneratorOptions() {
		return null;
	}

	@Override
	public boolean isFlatWorld() {
		return false;
	}

	@Override
	public boolean isDebugWorld() {
		return false;
	}

	@Override
	public Lifecycle getLifecycle() {
		return null;
	}
}
