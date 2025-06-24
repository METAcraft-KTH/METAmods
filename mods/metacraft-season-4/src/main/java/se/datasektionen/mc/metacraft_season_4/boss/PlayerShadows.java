package se.datasektionen.mc.metacraft_season_4.boss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.intprovider.IntProvider;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_lib.condition.conditions.NotInWall;
import se.datasektionen.mc.metacraft_lib.condition.conditions.SolidBlockBelow;
import se.datasektionen.mc.metacraft_lib.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_lib.extensions.LivingEntityExtensions;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.metacraft.bosses.boss.attacks.AttackType;
import se.metacraft.bosses.boss.attacks.InstantAttack;

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
		for (var player : ctx.boss().getPlayerTargets()) {
			var data = player.writeNbt(new NbtCompound());
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
				PlayerDataHelper.loadRootVehicleAndPassengers(
						shadow, data, e -> {
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

	@Override
	public AttackType getType() {
		return Season4Attacks.PLAYER_SHADOWS;
	}

}
