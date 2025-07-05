package nu.metacraft.season_4.boss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.math.intprovider.IntProvider;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.condition.conditions.SolidBlockBelow;
import nu.metacraft.lib.extensions.EntityExtensions;
import nu.metacraft.lib.extensions.LivingEntityExtensions;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.lib.util.helper.PlayerDataHelper;
import nu.metacraft.season_4.Season4;
import nu.metacraft.bosses.boss.attacks.AttackType;
import nu.metacraft.bosses.boss.attacks.InstantAttack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerShadows extends InstantAttack {

	public static final MapCodec<PlayerShadows> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					IntProvider.VALUE_CODEC.fieldOf("horizontal_range").forGetter(t -> t.horizontalRange),
					IntProvider.VALUE_CODEC.fieldOf("vertical_range").forGetter(t -> t.verticalRange)
			).apply(instance, PlayerShadows::new)
	);

	protected final IntProvider horizontalRange;
	protected final IntProvider verticalRange;

	public PlayerShadows(IntProvider horizontalRange, IntProvider verticalRange) {
		this.horizontalRange = horizontalRange;
		this.verticalRange = verticalRange;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerShadows#trigger", Season4.LOGGER)) {
			for (var player : ctx.boss().getPlayerTargets()) {
				var writeView = NbtWriteView.create(logging, ctx.getWorld().getRegistryManager());
				player.writeData(writeView);
				var data = writeView.getNbt();
				var shadow = METAcraftEntities.PLAYER.create(ctx.getWorld(), SpawnReason.REINFORCEMENT);
				shadow.copyFromPlayerData(data);
				((LivingEntityExtensions) shadow).metacraft_lib$setHostile(true);
				((EntityExtensions) shadow).metacraft_lib$setPreventEnterVehicle(true);

				var pos = EntityHelper.findPos(
						ctx.getWorld(), ctx.random(), player.getRootVehicle().getType(),
						new EntityHelper.SpawnEntry.SpawnRules(
								Optional.of(AllOfLootCondition.create(
										List.of(NotInWall.getInstance(), SolidBlockBelow.getInstance())
								)),
								SpawnReason.REINFORCEMENT,
								horizontalRange, verticalRange
						),
						player.getPos()
				);

				var team = ctx.boss().getScoreboardTeam();
				if (team != null) {
					ctx.getWorld().getScoreboard().addScoreHolderToTeam(
							shadow.getNameForScoreboard(), team
					);
				}

				shadow.setPos(pos.getX(), pos.getY(), pos.getZ());
				shadow.getBrain().remember(MemoryModuleType.ANGRY_AT, player.getUuid());

				if (ctx.getWorld().spawnEntity(shadow)) {
					var readView = NbtReadView.create(logging, ctx.getWorld().getRegistryManager(), data);
					PlayerDataHelper.loadRootVehicleAndPassengers(
							shadow, readView, e -> {
								e.setUuid(UUID.randomUUID());
								if (e instanceof LivingEntity) {
									((LivingEntityExtensions) e).metacraft_lib$setHostile(true);
								}
								e.setPos(pos.getX(), pos.getY(), pos.getZ());
								((EntityExtensions) e).metacraft_lib$setPreventEnterVehicle(true);
								if (e.getVehicle() instanceof VehicleEntity) {
									e.getVehicle().discard();
								}
								return ctx.getWorld().spawnEntity(e) ? e : null;
							}
					);
				}
			}
		}
	}

	@Override
	public AttackType getType() {
		return Season4Attacks.PLAYER_SHADOWS;
	}

}
