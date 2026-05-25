package nu.metacraft.faster_minecarts;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.event.RecipeLoad;
import nu.metacraft.lib.util.helper.RecipeHelper;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class FasterMinecarts implements ModInitializer {

	public static final String NAMESPACE = "faster_minecarts";

	public static final Logger LOGGER = LogManager.getLogger("faster-minecarts");

	public static final ResourceKey<DamageType> MINECART = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(NAMESPACE, "minecart"));

	public static final String MINECART_ITEM = NAMESPACE + ":minecart_item";

	public static final Identifier MINECART_IMPROVEMENTS = Identifier.withDefaultNamespace("minecart_improvements");

	@Override
	public void onInitialize() {
		FasterMinecartsConfig.getConfig();
		MinecartComponents.init();
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (recipe.getClass().equals(ShapedRecipe.class) || recipe.getClass().equals(ShapelessRecipe.class)) {
				Predicate<Item> isMinecart = item -> item instanceof MinecartItem;
				var recipeOutput = recipe.display().stream().map(RecipeDisplay::result).filter(
						d -> d instanceof SlotDisplay.ItemStackSlotDisplay
				).map(d -> ((SlotDisplay.ItemStackSlotDisplay) d).stack()).findFirst();
				if (
						recipeOutput.isPresent() &&
						isMinecart.test(recipeOutput.get().item().value()) &&
						recipe.placementInfo().ingredients().stream().anyMatch(
								i -> i.items().map(Holder::value).anyMatch(isMinecart)
						)
				) {
					RecipeHelper.addComponentCarryover(recipe, stack -> isMinecart.test(stack.typeHolder().value()), true);
				}
			}
			return recipe;
		});
	}

	public static BiPredicate<Entity, Entity> shouldBeDamaged = (entity, minecart) -> {
		if (minecart == entity.getRootVehicle()) {
			return false;
		}
		return FasterMinecartsConfig.getConfig(entity.level().getServer()).shouldDamageEntity(minecart.position(), entity);
	};

	public static void damageEntitiesFromCart(AbstractMinecart minecart, double actualSpeed, Vec3 movement) {
		Vec3 facing = movement.normalize();
		Vec3 left = facing.yRot((float) Math.PI / 2);
		double halfWidth = minecart.getBbWidth()/2;
		Vec3 boxStart = minecart.position().add(facing.scale(halfWidth));
		AABB ahead = new AABB(boxStart.add(left.scale(-halfWidth)), boxStart.add(facing.scale(movement.horizontalDistance())).add(left.scale(halfWidth)).add(0, minecart.getBbHeight(),0));
		damageEntitiesFromCart(minecart, actualSpeed, ahead);
	}

	public static void damageEntitiesFromCart(Entity minecart, double velocity, AABB box) {
		DamageSource source = new DamageSource(minecart.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(MINECART));
		if (FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().isPresent() && velocity > FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().get()) {
			for (Entity entity : minecart.level().getEntities(minecart, box, entity -> shouldBeDamaged.test(entity, minecart))) {
				float damage = (float) ((velocity - FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().get()) * FasterMinecartsConfig.getConfig().damageFactor());
				entity.hurtServer((ServerLevel) minecart.level(), source, damage);
			}
		}
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
