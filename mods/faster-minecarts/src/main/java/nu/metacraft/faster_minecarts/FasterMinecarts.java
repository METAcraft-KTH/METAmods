package nu.metacraft.faster_minecarts;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.MinecartItem;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.event.RecipeLoad;
import nu.metacraft.lib.util.helper.RecipeHelper;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class FasterMinecarts implements ModInitializer {

	public static final String NAMESPACE = "faster_minecarts";

	public static final Logger LOGGER = LogManager.getLogger("faster-minecarts");

	public static final RegistryKey<DamageType> MINECART = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(NAMESPACE, "minecart"));

	public static final String MINECART_ITEM = NAMESPACE + ":minecart_item";

	public static final Identifier MINECART_IMPROVEMENTS = Identifier.ofVanilla("minecart_improvements");

	@Override
	public void onInitialize() {
		FasterMinecartsConfig.getConfig();
		MinecartComponents.init();
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (recipe.getClass().equals(ShapedRecipe.class) || recipe.getClass().equals(ShapelessRecipe.class)) {
				Predicate<Item> isMinecart = item -> item instanceof MinecartItem;
				if (
						isMinecart.test(recipe.craft(null, registryLookup).getItem()) &&
						recipe.getIngredientPlacement().getIngredients().stream().anyMatch(
								i -> i.getMatchingItems().map(RegistryEntry::value).anyMatch(isMinecart)
						)
				) {
					RecipeHelper.addComponentCarryover(recipe, stack -> isMinecart.test(stack.getItem()), true);
				}
			}
			return recipe;
		});
	}

	public static BiPredicate<Entity, Entity> shouldBeDamaged = (entity, minecart) -> {
		if (minecart == entity.getRootVehicle()) {
			return false;
		}
		return FasterMinecartsConfig.getConfig(entity.getEntityWorld().getServer()).shouldDamageEntity(minecart.getPos(), entity);
	};

	public static void damageEntitiesFromCart(AbstractMinecartEntity minecart, double actualSpeed, Vec3d movement) {
		Vec3d facing = movement.normalize();
		Vec3d left = facing.rotateY((float) Math.PI / 2);
		double halfWidth = minecart.getWidth()/2;
		Vec3d boxStart = minecart.getPos().add(facing.multiply(halfWidth));
		Box ahead = new Box(boxStart.add(left.multiply(-halfWidth)), boxStart.add(facing.multiply(movement.horizontalLength())).add(left.multiply(halfWidth)).add(0, minecart.getHeight(),0));
		damageEntitiesFromCart(minecart, actualSpeed, ahead);
	}

	public static void damageEntitiesFromCart(Entity minecart, double velocity, Box box) {
		DamageSource source = new DamageSource(minecart.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(MINECART));
		if (FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().isPresent() && velocity > FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().get()) {
			for (Entity entity : minecart.getEntityWorld().getOtherEntities(minecart, box, entity -> shouldBeDamaged.test(entity, minecart))) {
				float damage = (float) ((velocity - FasterMinecartsConfig.getConfig().dangerousMinecartSpeed().get()) * FasterMinecartsConfig.getConfig().damageFactor());
				entity.damage((ServerWorld) minecart.getEntityWorld(), source, damage);
			}
		}
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
