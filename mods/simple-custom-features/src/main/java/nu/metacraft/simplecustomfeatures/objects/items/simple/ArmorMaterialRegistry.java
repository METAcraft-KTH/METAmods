package nu.metacraft.simplecustomfeatures.objects.items.simple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;

public class ArmorMaterialRegistry {

	public static final ResourceKey<Registry<ArmorMaterial>> KEY = ResourceKey.createRegistryKey(Features.getID("armor_material"));
	public static final Registry<ArmorMaterial> REGISTRY = FabricRegistryBuilder.create(KEY).buildAndRegister();

	private static final Codec<ArmorMaterial> INLINE_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.INT.fieldOf("durability").forGetter(ArmorMaterial::durability),
					Codec.simpleMap(
							ArmorType.CODEC, Codec.INT,
							StringRepresentable.keys(ArmorType.values())
					).fieldOf("defense").forGetter(ArmorMaterial::defense),
					Codec.INT.fieldOf("enchantment_value").forGetter(ArmorMaterial::enchantmentValue),
					SoundEvent.CODEC.fieldOf("equip_sound").forGetter(ArmorMaterial::equipSound),
					Codec.FLOAT.fieldOf("toughness").forGetter(ArmorMaterial::toughness),
					Codec.FLOAT.fieldOf("knockback_resistance").forGetter(ArmorMaterial::knockbackResistance),
					TagKey.hashedCodec(Registries.ITEM).fieldOf("repair_ingredient").forGetter(ArmorMaterial::repairIngredient),
					ResourceKey.codec(EquipmentAssets.ROOT_ID).fieldOf("asset_id").forGetter(ArmorMaterial::assetId)
			).apply(instance, ArmorMaterial::new)
	);

	public static final Codec<Holder<ArmorMaterial>> ENTRY_CODEC = RegistryFileCodec.create(KEY, INLINE_CODEC);
	public static final Codec<ArmorMaterial> CODEC = ENTRY_CODEC.xmap(Holder::value, REGISTRY::wrapAsHolder);


	public static void init() {
		Registry.register(REGISTRY, "leather", ArmorMaterials.LEATHER);
		Registry.register(REGISTRY, "chain", ArmorMaterials.CHAINMAIL);
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

		private final Holder<ArmorMaterial> material;

		public MaterialObject(Holder<ArmorMaterial> material) {
			this.material = material;
		}

		@Override
		public ObjectType<? extends BaseObject<ArmorMaterial>, ArmorMaterial> getType() {
			return ObjectRegistry.ARMOR_MATERIAL;
		}

		@Override
		public DataResult<ArmorMaterial> createObject(ResourceKey<ArmorMaterial> id) {
			return DataResult.success(material.value());
		}
	}
}
