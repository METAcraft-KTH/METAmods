import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.qol.Qol;
import nu.metacraft.qol.silence_mobs.SilenceMobs;

import java.util.Objects;
import java.util.function.Consumer;

public class SilenceMobsTests {

	static void register(String name, Consumer<GameTestHelper> test) {
		QolTests.register("silence_mobs/" + name, test);
	}

	/** A still cow in the middle of the room, named {@code name} if that isn't null. */
	static Cow cow(GameTestHelper ctx, String name) {
		var cow = EntityTypes.COW.create(ctx.getLevel(), EntitySpawnReason.COMMAND);
		var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(2, 1, 2)));
		cow.snapTo(at.x, at.y, at.z, 0, 0);
		cow.setNoAi(true);
		if (name != null) {
			cow.setCustomName(Component.literal(name));
		}
		ctx.getLevel().addFreshEntity(cow);
		return cow;
	}

	/** A player two blocks from the cow, whose client has "loaded" so the server accepts its interactions. */
	static ServerPlayer player(GameTestHelper ctx, GameType mode) {
		var player = TestHelper.addMockPlayer(ctx);
		player.setGameMode(mode);
		var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(2, 1, 0)));
		player.teleportTo(at.x, at.y, at.z);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}

	static ItemStack nameTag(String name) {
		var stack = new ItemStack(Items.NAME_TAG);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}

	/** Right-clicks the cow with {@code stack}, as the client's interact packet does. */
	static void useOn(ServerPlayer player, Cow cow, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.connection.handleInteract(new ServerboundInteractPacket(cow.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
	}

	static String name(Cow cow) {
		return cow.getCustomName() == null ? null : cow.getCustomName().getString();
	}

	/** Registers the silence mobs game tests; {@link QolTests} runs them with every other feature's. */
	static void registerTests() {
		register("silence_me_silences", ctx -> {
			var cow = cow(ctx, null);
			var player = player(ctx, GameType.SURVIVAL);
			var tag = nameTag("Silence me");
			useOn(player, cow, tag);
			ctx.assertTrue(cow.isSilent(), "the cow isn't silent");
			ctx.assertTrue(name(cow) == null, "the cow got named " + name(cow));
			ctx.assertTrue(tag.isEmpty(), "the name tag wasn't used up");
			// sparks show it worked, not Glowing, whose outline breaks shader packs
			ctx.assertTrue(!cow.hasEffect(MobEffects.GLOWING), "the cow glows; the outline breaks shader packs");
			ctx.succeed();
		});
		register("keeps_existing_name", ctx -> {
			var cow = cow(ctx, "Bob");
			useOn(player(ctx, GameType.SURVIVAL), cow, nameTag("silence_me"));
			ctx.assertTrue(cow.isSilent(), "the cow isn't silent");
			ctx.assertTrue(Objects.equals(name(cow), "Bob"), "the cow is now called " + name(cow));
			ctx.succeed();
		});
		register("any_case", ctx -> {
			var cow = cow(ctx, null);
			useOn(player(ctx, GameType.SURVIVAL), cow, nameTag("  SILENCE ME "));
			ctx.assertTrue(cow.isSilent(), "the cow isn't silent");
			ctx.succeed();
		});
		register("unsilence_me", ctx -> {
			var cow = cow(ctx, "Bob");
			cow.setSilent(true);
			useOn(player(ctx, GameType.SURVIVAL), cow, nameTag("Unsilence Me"));
			ctx.assertTrue(!cow.isSilent(), "the cow is still silent");
			ctx.assertTrue(Objects.equals(name(cow), "Bob"), "the cow is now called " + name(cow));
			ctx.succeed();
		});
		register("other_names_name_normally", ctx -> {
			var cow = cow(ctx, null);
			var tag = nameTag("Daisy");
			useOn(player(ctx, GameType.SURVIVAL), cow, tag);
			ctx.assertTrue(Objects.equals(name(cow), "Daisy"), "the cow is called " + name(cow) + ", expected Daisy");
			ctx.assertTrue(!cow.isSilent(), "naming the cow silenced it");
			ctx.assertTrue(tag.isEmpty(), "the name tag wasn't used up");
			ctx.succeed();
		});
		register("creative_keeps_tag", ctx -> {
			var cow = cow(ctx, null);
			var tag = nameTag("silence me");
			useOn(player(ctx, GameType.CREATIVE), cow, tag);
			ctx.assertTrue(cow.isSilent(), "the cow isn't silent");
			ctx.assertTrue(tag.getCount() == 1, "creative used up the name tag");
			ctx.succeed();
		});
		register("muffler_toggles", ctx -> {
			var cow = cow(ctx, null);
			var player = player(ctx, GameType.SURVIVAL);
			var muffler = new ItemStack(SilenceMobs.MUFFLER);
			useOn(player, cow, muffler);
			ctx.assertTrue(cow.isSilent(), "the muffler didn't silence the cow");
			useOn(player, cow, muffler);
			ctx.assertTrue(!cow.isSilent(), "a second use didn't unsilence the cow");
			ctx.assertTrue(muffler.getCount() == 1, "the muffler was used up");
			ctx.assertTrue(name(cow) == null, "the cow got named " + name(cow));
			ctx.succeed();
		});
		register("muffler_keeps_name", ctx -> {
			var cow = cow(ctx, "Bob");
			useOn(player(ctx, GameType.SURVIVAL), cow, new ItemStack(SilenceMobs.MUFFLER));
			ctx.assertTrue(cow.isSilent(), "the muffler didn't silence the cow");
			ctx.assertTrue(Objects.equals(name(cow), "Bob"), "the cow is now called " + name(cow));
			ctx.succeed();
		});
		register("muffler_recipe", ctx -> {
			var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Qol.MODID, "muffler"));
			ctx.assertTrue(ctx.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no " + key.identifier() + " recipe");
			ctx.succeed();
		});
		register("off_muffler_does_nothing", ctx -> {
			var cow = cow(ctx, null);
			var muffler = new ItemStack(SilenceMobs.MUFFLER);
			useOn(player(ctx, GameType.SURVIVAL), cow, muffler);
			ctx.assertTrue(!cow.isSilent(), "the muffler silenced the cow while silence mobs is off");
			ctx.assertTrue(muffler.getCount() == 1, "the muffler was used up");
			ctx.succeed();
		});
		register("off_names_normally", ctx -> {
			var cow = cow(ctx, null);
			useOn(player(ctx, GameType.SURVIVAL), cow, nameTag("silence me"));
			ctx.assertTrue(!cow.isSilent(), "the cow was silenced while silence mobs is off");
			ctx.assertTrue(Objects.equals(name(cow), "silence me"), "the cow is called " + name(cow) + ", expected the vanilla name");
			ctx.succeed();
		});
	}

}
