package se.metacraft.portable_jukebox.gametest;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.references.ItemIds;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;
import se.metacraft.portable_jukebox.block.Blocks;
import se.metacraft.portable_jukebox.block.PortableJukeboxBlockEntity;
import se.metacraft.portable_jukebox.item.Items;
import se.metacraft.portable_jukebox.item.components.Components;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class DataFixTest implements CustomTestMethodInvoker {

	private static Map<?, ?> upgradableItem() {
		return Map.of(
			"id", ItemIds.MUSIC_DISC_PIGSTEP.identifier().toString(),
			"count", 1,
			"components", Map.of(
				"minecraft:fire_resistant", Map.of()
			)
		);
	}

	private static void validateItem(GameTestHelper helper, ItemStackTemplate item) {
		if (!item.is(net.minecraft.world.item.Items.MUSIC_DISC_PIGSTEP)) {
			helper.fail("Not pigstep");
		}

		var damageResistant = item.get(DataComponents.DAMAGE_RESISTANT);
		if (damageResistant == null) {
			helper.fail("Damage resistant missing");
			return;
		}
		if (damageResistant.types() != helper.getLevel().registryAccess().getOrThrow(DamageTypeTags.IS_FIRE)) {
			helper.fail("Not fire resistant");
		}
	}

	private static void validateJukebox(GameTestHelper helper, ItemStack jukebox) {
		if (jukebox.is(Items.PORTABLE_JUKEBOX)) {
			var data = jukebox.get(Components.PORTABLE_JUKEBOX);
			if (data == null || data.item().isEmpty()) {
				helper.fail("Data went missing");
				return;
			}
			validateItem(helper, data.item().get());

			var storage = jukebox.get(Components.PORTABLE_JUKEBOX_STORAGE);
			if (storage == null) {
				helper.fail("Data went missing");
				return;
			}
			NonNullList<ItemStack> items = NonNullList.withSize(storage.size(), ItemStack.EMPTY);
			storage.copyInto(items);
			validateItem(helper, ItemStackTemplate.fromNonEmptyStack(items.get(0)));
			validateItem(helper, ItemStackTemplate.fromNonEmptyStack(items.get(1)));
			validateItem(helper, ItemStackTemplate.fromNonEmptyStack(items.get(5)));
		} else {
			helper.fail("Not a jukebox");
		}
	}

	private Object upgradableSlot(int slot) {
		return Map.of(
			"item", upgradableItem(),
			"slot", slot
		);
	}

	private Object upgradableSlots() {
		return List.of(
			upgradableSlot(0),
			upgradableSlot(1),
			upgradableSlot(5)
		);
	}

	@GameTest
	public void jukeboxItem(GameTestHelper helper) {
		var stack = helper.getLevel().getServer().getFixerUpper().update(
			References.ITEM_STACK, new Dynamic<>(
				JavaOps.INSTANCE,
				Map.of(
					"id", Items.PORTABLE_JUKEBOX.builtInRegistryHolder().key().identifier().toString(),
					"components", Map.of(
						BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(Components.PORTABLE_JUKEBOX).toString(),
						upgradableItem(),
						BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(Components.PORTABLE_JUKEBOX_STORAGE).toString(),
						upgradableSlots()
					)
				)
			),
			4059,
			SharedConstants.getCurrentVersion().dataVersion().version()
		);

		validateJukebox(
			helper,
			ItemStack.CODEC.parse(
				helper.getLevel().registryAccess().createSerializationContext(JavaOps.INSTANCE), stack.getValue()
			).getOrThrow()
		);
		helper.succeed();
	}

	@GameTest
	public void jukeboxBlock(GameTestHelper helper) {
		var data = helper.getLevel().getServer().getFixerUpper().update(
			References.BLOCK_ENTITY, new Dynamic<>(
				JavaOps.INSTANCE,
				Map.of(
					"id", Blocks.BlockEntities.PORTABLE_JUKEBOX.builtInRegistryHolder().key().identifier().toString(),
					"Jukebox", Map.of(
						"id", Items.PORTABLE_JUKEBOX.builtInRegistryHolder().key().identifier().toString(),
						"components", Map.of(
							BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(Components.PORTABLE_JUKEBOX).toString(),
							upgradableItem(),
							BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(Components.PORTABLE_JUKEBOX_STORAGE).toString(),
							upgradableSlots()
						)
					)
				)
			),
			4059,
			SharedConstants.getCurrentVersion().dataVersion().version()
		);

		var blockEntity = BlockEntity.loadStatic(
			BlockPos.ZERO, Blocks.PORTABLE_JUKEBOX.defaultBlockState(),
			(CompoundTag) JavaOps.INSTANCE.convertTo(NbtOps.INSTANCE, data.getValue()),
			helper.getLevel().registryAccess()
		);

		if (blockEntity instanceof PortableJukeboxBlockEntity jukebox) {
			validateJukebox(helper, jukebox.getJukebox());
		} else {
			helper.fail("Not a jukebox");
		}

		helper.succeed();
	}

	@Override
	public void invokeTestMethod(
		@NonNull GameTestHelper helper, @NonNull Method method
	) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
