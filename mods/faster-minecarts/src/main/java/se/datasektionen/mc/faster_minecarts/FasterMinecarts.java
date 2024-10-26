package se.datasektionen.mc.faster_minecarts;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MinecartItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.event.RecipeLoad;
import se.datasektionen.mc.metacraft_lib.util.helper.RecipeHelper;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class FasterMinecarts implements ModInitializer {

	public static final String NAMESPACE = "faster_minecarts";

	public static final Logger logger = LogManager.getLogger("faster-minecarts");

	public static final RegistryKey<DamageType> MINECART = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(NAMESPACE, "minecart"));

	public static final String SPEED_UPGRADE_KEY = "SpeedUpgrade";
	public static final String ACCELERATION = "Acceleration";
	public static final String MAX_SPEED = "MaxSpeed";
	public static final String MAX_SPEED_UNDERWATER = "MaxSpeedUnderwater";
	public static final String CRAFTING_TAG = "CraftingTag";


	public static void modifyEntityFromItem(ItemStack stack, AbstractMinecartEntity minecart) {
		if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
			var nbt = stack.get(DataComponentTypes.CUSTOM_DATA);
			nbt.get(Codec.BOOL.optionalFieldOf(SPEED_UPGRADE_KEY)).resultOrPartial(FasterMinecarts.logger::error).ifPresent(
					speedUpgrade -> speedUpgrade.ifPresent(
							s -> ((MinecartData) minecart).fasterMinecarts$setSuperFast(s)
					)
			);
			nbt.get(Codec.DOUBLE.optionalFieldOf(FasterMinecarts.ACCELERATION)).resultOrPartial(
					FasterMinecarts.logger::error
			).ifPresent(acceleration -> {
				((MinecartData) minecart).fasterMinecarts$setAcceleration(
						acceleration.stream().mapToDouble(n -> n).findAny()
				);
			});
			nbt.get(Codec.DOUBLE.optionalFieldOf(FasterMinecarts.MAX_SPEED)).resultOrPartial(
					FasterMinecarts.logger::error
			).ifPresent(maxSpeed -> {
				((MinecartData) minecart).fasterMinecarts$setMaxSpeed(
						maxSpeed.stream().mapToDouble(n -> n).findAny()
				);
			});
			nbt.get(Codec.DOUBLE.optionalFieldOf(FasterMinecarts.MAX_SPEED_UNDERWATER)).resultOrPartial(
					FasterMinecarts.logger::error
			).ifPresent(maxSpeed -> {
				((MinecartData) minecart).fasterMinecarts$setMaxSpeedUnderwater(
						maxSpeed.stream().mapToDouble(n -> n).findAny()
				);
			});
			nbt.get(Codec.STRING.optionalFieldOf(FasterMinecarts.CRAFTING_TAG)).resultOrPartial(
					FasterMinecarts.logger::error
			).ifPresent(tag -> {
				((MinecartData) minecart).fasterMinecarts$setCraftingTag(tag);
			});
			((MinecartData) minecart).fasterMinecarts$setItemName(Optional.ofNullable(
					stack.get(DataComponentTypes.ITEM_NAME)
			));
		}
	}

	public static ItemStack makeSuperFastMinecartItem(
			ItemStack stack, OptionalDouble acceleration, OptionalDouble maxSpeed, OptionalDouble maxSpeedUnderwater,
			Optional<String> craftingTag, Text name
	) {
		makeSuperFastMinecartItem(stack, acceleration, maxSpeed, maxSpeedUnderwater, craftingTag, Optional.of(name));
		return stack;
	}

	public static void makeSuperFastMinecartItem(
			ItemStack stack, OptionalDouble acceleration, OptionalDouble maxSpeed, OptionalDouble maxSpeedUnderwater,
			Optional<String> craftingTag, Optional<Text> name
	) {
		var data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));
		stack.set(
				DataComponentTypes.CUSTOM_DATA,
				data.apply(nbt -> {
					nbt.putBoolean(SPEED_UPGRADE_KEY, true);
					acceleration.ifPresent(a -> nbt.putDouble(ACCELERATION, a));
					maxSpeed.ifPresent(m -> nbt.putDouble(MAX_SPEED, m));
					maxSpeedUnderwater.ifPresent(m -> nbt.putDouble(MAX_SPEED_UNDERWATER, m));
					craftingTag.ifPresent(tag -> nbt.putString(FasterMinecarts.CRAFTING_TAG, tag));
				})
		);

		name.ifPresentOrElse(itemName -> {
			stack.set(DataComponentTypes.ITEM_NAME, itemName);
		}, () -> {
			if (!stack.contains(DataComponentTypes.ITEM_NAME)) {
				stack.set(
						DataComponentTypes.ITEM_NAME,
						Text.literal("Fast ").append(stack.getOrDefault(
								DataComponentTypes.ITEM_NAME, stack.getItem().getName()
						))
				);
			}
		});
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
	}

	@Override
	public void onInitialize() {
		FasterMinecartsConfig.init();
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (recipe.getClass().equals(ShapedRecipe.class) || recipe.getClass().equals(ShapelessRecipe.class)) {
				Predicate<Item> isMinecart = item -> item instanceof MinecartItem;
				if (
						isMinecart.test(recipe.craft(null, registryLookup).getItem()) &&
						recipe.getIngredientPlacement().getIngredients().stream().anyMatch(
								i -> i.getMatchingItems().stream().map(RegistryEntry::value).anyMatch(isMinecart)
						)
				) {
					RecipeHelper.addComponentCarryover(recipe, stack -> isMinecart.test(stack.getItem()), true);
				}
			}
			return recipe;
		});
	}

	public static BiPredicate<Entity, Entity> shouldBeDamaged = (entity, minecart) -> {
		if (minecart.hasPassenger(entity.getRootVehicle())) {
			return false;
		}
		return FasterMinecartsConfig.getEntityDamageBlacklist().shouldDamageEntity(entity);
	};

	public static void damageEntitiesFromCart(Entity minecart, double velocity, Box box) {
		DamageSource source = new DamageSource(minecart.getWorld().getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(MINECART));
		if (velocity > FasterMinecartsConfig.getConfig().dangerousMinecartSpeed && FasterMinecartsConfig.getConfig().dangerousMinecartSpeed > 0) {
			for (Entity entity : minecart.getWorld().getOtherEntities(minecart, box, entity -> shouldBeDamaged.test(entity, minecart))) {
				float damage = (float) ((velocity - FasterMinecartsConfig.getConfig().dangerousMinecartSpeed) * FasterMinecartsConfig.getConfig().damageFactor);
				entity.damage((ServerWorld) minecart.getWorld(), source, damage);
			}
		}
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}
