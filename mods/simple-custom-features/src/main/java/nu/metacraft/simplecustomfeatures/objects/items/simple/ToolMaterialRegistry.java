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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ToolMaterial;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;

public class ToolMaterialRegistry {

	public static final ResourceKey<Registry<ToolMaterial>> KEY = ResourceKey.createRegistryKey(Features.getID("tool_material"));
	public static final Registry<ToolMaterial> REGISTRY = FabricRegistryBuilder.createSimple(KEY).buildAndRegister();

	private static final Codec<ToolMaterial> INLINE_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					TagKey.hashedCodec(Registries.BLOCK).fieldOf("incorrect_blocks_for_drops").forGetter(ToolMaterial::incorrectBlocksForDrops),
					Codec.INT.fieldOf("durability").forGetter(ToolMaterial::durability),
					Codec.FLOAT.fieldOf("speed").forGetter(ToolMaterial::speed),
					Codec.FLOAT.fieldOf("attack_damage_bonus").forGetter(ToolMaterial::attackDamageBonus),
					Codec.INT.fieldOf("enchantment_value").forGetter(ToolMaterial::enchantmentValue),
					TagKey.hashedCodec(Registries.ITEM).fieldOf("repair_items").forGetter(ToolMaterial::repairItems)
			).apply(instance, ToolMaterial::new)
	);

	public static final Codec<Holder<ToolMaterial>> ENTRY_CODEC = RegistryFileCodec.create(KEY, INLINE_CODEC);
	public static final Codec<ToolMaterial> CODEC = ENTRY_CODEC.xmap(Holder::value, REGISTRY::wrapAsHolder);


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

		private final Holder<ToolMaterial> material;

		public MaterialObject(Holder<ToolMaterial> material) {
			this.material = material;
		}

		@Override
		public ObjectType<? extends BaseObject<ToolMaterial>, ToolMaterial> getType() {
			return ObjectRegistry.TOOL_MATERIAL;
		}

		@Override
		public DataResult<ToolMaterial> createObject(ResourceKey<ToolMaterial> id) {
			return DataResult.success(material.value());
		}
	}
}
