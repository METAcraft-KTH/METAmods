import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.util.helper.RegistryDependentCodecHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestInit.class)
public class TestRegistryDependenceDetector {

	private static void check(Codec<?> codec, boolean value) {
		assert value == RegistryDependentCodecHelper.isRegistryDependent(
				codec, name -> {}
		);
	}

	@Test
	public void checkRegistrations() {
		check(Codec.STRING, false);
		check(UUIDUtil.CODEC_LINKED_SET, false);
		check(RegistryFixedCodec.create(Registries.ENTITY_TYPE), true);
		check(Codec.unboundedMap(Codec.STRING, RegistryFixedCodec.create(Registries.ENTITY_TYPE)), true);
		check(LootItemCondition.DIRECT_CODEC, true);
		check(ItemStack.CODEC, true); //Because of enchantment component. Might not always be on each item stack, but it "could be".
		check(ComponentSerialization.CODEC, true); //Because of item stack above.
		check(CompoundTag.CODEC, false);
		check(Level.RESOURCE_KEY_CODEC, false);
	}

}
