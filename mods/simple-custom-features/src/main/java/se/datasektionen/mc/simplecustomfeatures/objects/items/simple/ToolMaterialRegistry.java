package se.datasektionen.mc.simplecustomfeatures.objects.items.simple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

public class ToolMaterialRegistry {

	public static final RegistryKey<Registry<ToolMaterial>> KEY = RegistryKey.ofRegistry(Features.getID("tool_material"));
	public static final Registry<ToolMaterial> REGISTRY = FabricRegistryBuilder.createSimple(KEY).buildAndRegister();

	private static final Codec<ToolMaterial> INLINE_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					TagKey.codec(RegistryKeys.BLOCK).fieldOf("incorrect_blocks_for_drops").forGetter(ToolMaterial::incorrectBlocksForDrops),
					Codec.INT.fieldOf("durability").forGetter(ToolMaterial::durability),
					Codec.FLOAT.fieldOf("speed").forGetter(ToolMaterial::speed),
					Codec.FLOAT.fieldOf("attack_damage_bonus").forGetter(ToolMaterial::attackDamageBonus),
					Codec.INT.fieldOf("enchantment_value").forGetter(ToolMaterial::enchantmentValue),
					TagKey.codec(RegistryKeys.ITEM).fieldOf("repair_items").forGetter(ToolMaterial::repairItems)
			).apply(instance, ToolMaterial::new)
	);

	public static final Codec<RegistryEntry<ToolMaterial>> ENTRY_CODEC = RegistryElementCodec.of(KEY, INLINE_CODEC);
	public static final Codec<ToolMaterial> CODEC = ENTRY_CODEC.xmap(RegistryEntry::value, REGISTRY::getEntry);


	public static void init() {
		Registry.register(REGISTRY, "wood", ToolMaterial.WOOD);
		Registry.register(REGISTRY, "stone", ToolMaterial.STONE);
		Registry.register(REGISTRY, "iron", ToolMaterial.IRON);
		Registry.register(REGISTRY, "diamond", ToolMaterial.DIAMOND);
		Registry.register(REGISTRY, "gold", ToolMaterial.GOLD);
		Registry.register(REGISTRY, "netherite", ToolMaterial.NETHERITE);
	}

	public static class MaterialObject implements BaseObject<ToolMaterial> {

		public static final MapCodec<MaterialObject> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						ENTRY_CODEC.fieldOf("tool_material").forGetter(m -> m.material)
				).apply(instance, MaterialObject::new)
		);

		private final RegistryEntry<ToolMaterial> material;

		public MaterialObject(RegistryEntry<ToolMaterial> material) {
			this.material = material;
		}

		@Override
		public ObjectType<? extends BaseObject<ToolMaterial>, ToolMaterial> getType() {
			return ObjectRegistry.TOOL_MATERIAL;
		}

		@Override
		public DataResult<ToolMaterial> createObject(RegistryKey<ToolMaterial> id) {
			return DataResult.success(material.value());
		}
	}
}
