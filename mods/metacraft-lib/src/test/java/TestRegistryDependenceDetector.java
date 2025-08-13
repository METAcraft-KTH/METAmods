import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryFixedCodec;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.RegistryDependentCodecHelper;
import nu.metacraft.lib.util.helper.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestRegistryDependenceDetector {

	@BeforeAll
	public static void init() {
		TestHelper.init(METAcraftLib::new);
	}

	private static void check(Codec<?> codec, boolean value) {
		assert value == RegistryDependentCodecHelper.isRegistryDependent(
				codec, name -> {}
		);
	}

	@Test
	public void checkRegistrations() {
		check(Codec.STRING, false);
		check(Uuids.LINKED_SET_CODEC, false);
		check(RegistryFixedCodec.of(RegistryKeys.ENTITY_TYPE), true);
		check(Codec.unboundedMap(Codec.STRING, RegistryFixedCodec.of(RegistryKeys.ENTITY_TYPE)), true);
		check(LootCondition.CODEC, true);
		check(ItemStack.CODEC, true); //Because of enchantment component. Might not always be on each item stack, but it "could be".
		check(TextCodecs.CODEC, true); //Because of item stack above.
		check(NbtCompound.CODEC, false);
		check(World.CODEC, false);
	}

}
