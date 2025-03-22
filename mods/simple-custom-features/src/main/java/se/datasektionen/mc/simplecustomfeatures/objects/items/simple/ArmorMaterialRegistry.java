package se.datasektionen.mc.simplecustomfeatures.objects.items.simple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.StringIdentifiable;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

public class ArmorMaterialRegistry {

	public static final RegistryKey<Registry<ArmorMaterial>> KEY = RegistryKey.ofRegistry(Features.getID("armor_material"));
	public static final Registry<ArmorMaterial> REGISTRY = FabricRegistryBuilder.createSimple(KEY).buildAndRegister();

	private static final Codec<ArmorMaterial> INLINE_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.INT.fieldOf("durability").forGetter(ArmorMaterial::durability),
					Codec.simpleMap(
							EquipmentType.CODEC, Codec.INT,
							StringIdentifiable.toKeyable(EquipmentType.values())
					).fieldOf("defense").forGetter(ArmorMaterial::defense),
					Codec.INT.fieldOf("enchantment_value").forGetter(ArmorMaterial::enchantmentValue),
					SoundEvent.ENTRY_CODEC.fieldOf("equip_sound").forGetter(ArmorMaterial::equipSound),
					Codec.FLOAT.fieldOf("toughness").forGetter(ArmorMaterial::toughness),
					Codec.FLOAT.fieldOf("knockback_resistance").forGetter(ArmorMaterial::knockbackResistance),
					TagKey.codec(RegistryKeys.ITEM).fieldOf("repair_ingredient").forGetter(ArmorMaterial::repairIngredient),
					RegistryKey.createCodec(EquipmentAssetKeys.REGISTRY_KEY).fieldOf("asset_id").forGetter(ArmorMaterial::assetId)
			).apply(instance, ArmorMaterial::new)
	);

	public static final Codec<RegistryEntry<ArmorMaterial>> ENTRY_CODEC = RegistryElementCodec.of(KEY, INLINE_CODEC);
	public static final Codec<ArmorMaterial> CODEC = ENTRY_CODEC.xmap(RegistryEntry::value, REGISTRY::getEntry);


	public static void init() {
		Registry.register(REGISTRY, "leather", ArmorMaterials.LEATHER);
		Registry.register(REGISTRY, "chain", ArmorMaterials.CHAIN);
		Registry.register(REGISTRY, "iron", ArmorMaterials.IRON);
		Registry.register(REGISTRY, "gold", ArmorMaterials.GOLD);
		Registry.register(REGISTRY, "diamond", ArmorMaterials.DIAMOND);
		Registry.register(REGISTRY, "turtle_scute", ArmorMaterials.TURTLE_SCUTE);
		Registry.register(REGISTRY, "netherite", ArmorMaterials.NETHERITE);
		Registry.register(REGISTRY, "armadillo_scute", ArmorMaterials.ARMADILLO_SCUTE);
	}

	public static class MaterialObject implements BaseObject<ArmorMaterial> {

		public static final MapCodec<MaterialObject> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						ENTRY_CODEC.fieldOf("tool_material").forGetter(m -> m.material)
				).apply(instance, MaterialObject::new)
		);

		private final RegistryEntry<ArmorMaterial> material;

		public MaterialObject(RegistryEntry<ArmorMaterial> material) {
			this.material = material;
		}

		@Override
		public ObjectType<? extends BaseObject<ArmorMaterial>, ArmorMaterial> getType() {
			return ObjectRegistry.ARMOR_MATERIAL;
		}

		@Override
		public DataResult<ArmorMaterial> createObject(RegistryKey<ArmorMaterial> id) {
			return DataResult.success(material.value());
		}
	}
}
