package se.datasektionen.mc.metacraft_season_4;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.client.data.*;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.item.model.SelectItemModel;
import net.minecraft.client.render.item.property.select.TrimMaterialProperty;
import net.minecraft.client.render.item.tint.DyeTintSource;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.trim.ArmorTrimAssets;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.pcollections.OrderedPSet;
import org.pcollections.PSet;

import java.util.*;
import java.util.stream.Collectors;

public class Season4Datagen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		fabricDataGenerator.createPack().addProvider(TrimProvider::new);
	}

	public static class TrimProvider extends FabricModelProvider {

		private final PSet<ItemModelGenerator.TrimMaterial> customMaterials = OrderedPSet.singleton(
				createArmourTrim("datalogi")
		);

		private final Collection<ItemModelGenerator.TrimMaterial> allMaterials = customMaterials.plusAll(ItemModelGenerator.TRIM_MATERIALS);

		public TrimProvider(FabricDataOutput output) {
			super(output);
		}

		private static ItemModelGenerator.TrimMaterial createArmourTrim(String key) {
			return createArmourTrim(key, Map.of());
		}

		private static ItemModelGenerator.TrimMaterial createArmourTrim(
				String key, Map<RegistryKey<EquipmentAsset>, String> armourSpecificOverrides
		) {
			return new ItemModelGenerator.TrimMaterial(
					new ArmorTrimAssets(
							new ArmorTrimAssets.AssetId(key),
							armourSpecificOverrides.entrySet().stream().map(
									e -> Pair.of(e.getKey(), new ArmorTrimAssets.AssetId(e.getValue()))
							).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
					),
					RegistryKey.of(RegistryKeys.TRIM_MATERIAL, Season4.getID(key))
			);
		}

		@Override
		public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {

		}

		@Override
		public void generateItemModels(ItemModelGenerator itemModelGenerator) {
			generateArmours(itemModelGenerator);
		}

		private void registerArmour(
				ItemModelGenerator itemModelGenerator,
				Item item, RegistryKey<EquipmentAsset> equipmentKey,
				String type, boolean dyeable
		) {
			Identifier identifier = ModelIds.getItemModelId(item);
			Identifier identifier2 = TextureMap.getId(item);
			Identifier identifier3 = TextureMap.getSubId(item, "_overlay");
			List<SelectItemModel.SwitchCase<RegistryKey<ArmorTrimMaterial>>> list = new ArrayList<>(allMaterials.size());

			for(ItemModelGenerator.TrimMaterial trimMaterial : allMaterials) {
				Identifier identifier4 = Identifier.of(
						trimMaterial.materialKey().getValue().getNamespace(),
						identifier.getPath() + "_" + trimMaterial.assets().base().suffix() + "_trim"
				);
				Identifier identifier5 = Identifier.ofVanilla(
						"trims/items/" + type + "_trim_" + trimMaterial.assets().getAssetId(equipmentKey).suffix()
				);
				ItemModel.Unbaked unbaked;
				if (dyeable) {
					if (customMaterials.contains(trimMaterial)) {
						itemModelGenerator.uploadArmor(identifier4, identifier2, identifier3, identifier5);
					}
					unbaked = ItemModels.tinted(identifier4, new DyeTintSource(-6265536));
				} else {
					if (customMaterials.contains(trimMaterial)) {
						itemModelGenerator.uploadArmor(identifier4, identifier2, identifier5);
					}
					unbaked = ItemModels.basic(identifier4);
				}

				list.add(ItemModels.switchCase(trimMaterial.materialKey(), unbaked));
			}

			ItemModel.Unbaked unbaked2;
			if (dyeable) {
				unbaked2 = ItemModels.tinted(identifier, new DyeTintSource(-6265536));
			} else {
				unbaked2 = ItemModels.basic(identifier);
			}

			itemModelGenerator.output.accept(item, ItemModels.select(new TrimMaterialProperty(), unbaked2, list));
		}

		private void generateArmours(ItemModelGenerator generator) {
			registerArmour(generator, Items.TURTLE_HELMET, EquipmentAssetKeys.TURTLE_SCUTE, "helmet", false);
			registerArmour(generator, Items.LEATHER_HELMET, EquipmentAssetKeys.LEATHER, "helmet", true);
			registerArmour(generator, Items.LEATHER_CHESTPLATE, EquipmentAssetKeys.LEATHER, "chestplate", true);
			registerArmour(generator, Items.LEATHER_LEGGINGS, EquipmentAssetKeys.LEATHER, "leggings", true);
			registerArmour(generator, Items.LEATHER_BOOTS, EquipmentAssetKeys.LEATHER, "boots", true);
			registerArmour(generator, Items.CHAINMAIL_HELMET, EquipmentAssetKeys.CHAINMAIL, "helmet", false);
			registerArmour(generator, Items.CHAINMAIL_CHESTPLATE, EquipmentAssetKeys.CHAINMAIL, "chestplate", false);
			registerArmour(generator, Items.CHAINMAIL_LEGGINGS, EquipmentAssetKeys.CHAINMAIL, "leggings", false);
			registerArmour(generator, Items.CHAINMAIL_BOOTS, EquipmentAssetKeys.CHAINMAIL, "boots", false);
			registerArmour(generator, Items.IRON_HELMET, EquipmentAssetKeys.IRON, "helmet", false);
			registerArmour(generator, Items.IRON_CHESTPLATE, EquipmentAssetKeys.IRON, "chestplate", false);
			registerArmour(generator, Items.IRON_LEGGINGS, EquipmentAssetKeys.IRON, "leggings", false);
			registerArmour(generator, Items.IRON_BOOTS, EquipmentAssetKeys.IRON, "boots", false);
			registerArmour(generator, Items.DIAMOND_HELMET, EquipmentAssetKeys.DIAMOND, "helmet", false);
			registerArmour(generator, Items.DIAMOND_CHESTPLATE, EquipmentAssetKeys.DIAMOND, "chestplate", false);
			registerArmour(generator, Items.DIAMOND_LEGGINGS, EquipmentAssetKeys.DIAMOND, "leggings", false);
			registerArmour(generator, Items.DIAMOND_BOOTS, EquipmentAssetKeys.DIAMOND, "boots", false);
			registerArmour(generator, Items.GOLDEN_HELMET, EquipmentAssetKeys.GOLD, "helmet", false);
			registerArmour(generator, Items.GOLDEN_CHESTPLATE, EquipmentAssetKeys.GOLD, "chestplate", false);
			registerArmour(generator, Items.GOLDEN_LEGGINGS, EquipmentAssetKeys.GOLD, "leggings", false);
			registerArmour(generator, Items.GOLDEN_BOOTS, EquipmentAssetKeys.GOLD, "boots", false);
			registerArmour(generator, Items.NETHERITE_HELMET, EquipmentAssetKeys.NETHERITE, "helmet", false);
			registerArmour(generator, Items.NETHERITE_CHESTPLATE, EquipmentAssetKeys.NETHERITE, "chestplate", false);
			registerArmour(generator, Items.NETHERITE_LEGGINGS, EquipmentAssetKeys.NETHERITE, "leggings", false);
			registerArmour(generator, Items.NETHERITE_BOOTS, EquipmentAssetKeys.NETHERITE, "boots", false);
		}
	}
}
