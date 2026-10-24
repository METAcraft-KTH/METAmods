import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import se.metacraft.lib.util.helper.TestHelper;
import se.metacraft.qol.Qol;
import se.metacraft.qol.silence_mobs.SilenceMobs;

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
		register("echo_fork_toggles", ctx -> {
			var cow = cow(ctx, null);
			var player = player(ctx, GameType.SURVIVAL);
			var fork = new ItemStack(SilenceMobs.ECHO_FORK);
			useOn(player, cow, fork);
			ctx.assertTrue(cow.isSilent(), "the echo fork didn't silence the cow");
			useOn(player, cow, fork);
			ctx.assertTrue(!cow.isSilent(), "a second use didn't unsilence the cow");
			ctx.assertTrue(fork.getCount() == 1, "the echo fork was used up");
			ctx.assertTrue(name(cow) == null, "the cow got named " + name(cow));
			ctx.succeed();
		});
		register("echo_fork_keeps_name", ctx -> {
			var cow = cow(ctx, "Bob");
			useOn(player(ctx, GameType.SURVIVAL), cow, new ItemStack(SilenceMobs.ECHO_FORK));
			ctx.assertTrue(cow.isSilent(), "the echo fork didn't silence the cow");
			ctx.assertTrue(Objects.equals(name(cow), "Bob"), "the cow is now called " + name(cow));
			ctx.succeed();
		});
		register("echo_fork_swings_the_arm", ctx -> {
			var cow = cow(ctx, null);
			var player = player(ctx, GameType.SURVIVAL);
			QolTests.sentTo(player);
			useOn(player, cow, new ItemStack(SilenceMobs.ECHO_FORK));
			ctx.assertTrue(cow.isSilent(), "the echo fork didn't silence the cow");
			ctx.assertTrue(player.isSwinging(), "the player's arm didn't swing");
			ctx.assertTrue(player.getCurrentSwing().hand() == InteractionHand.MAIN_HAND, "swung " + player.getCurrentSwing().hand());
			// the client sees a brick and predicts nothing, so the server must tell the user's own client too
			boolean toSelf = QolTests.sentTo(player).stream().anyMatch(packet ->
					packet instanceof ClientboundSwingAnimationPacket swing && swing.entityId() == player.getId()
			);
			ctx.assertTrue(toSelf, "the player's own client wasn't told to swing");
			ctx.succeed();
		});
		register("echo_fork_item_interaction", ctx -> {
			var cow = cow(ctx, null);
			var player = player(ctx, GameType.SURVIVAL);
			var fork = new ItemStack(SilenceMobs.ECHO_FORK);
			var result = fork.interactLivingEntity(player, cow, InteractionHand.MAIN_HAND);
			ctx.assertTrue(cow.isSilent(), "the echo fork didn't silence the cow");
			ctx.assertTrue(
					result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.SERVER_ONLY,
					"the echo fork's interaction returned " + result + ", expected a server-swung success"
			);
			ctx.succeed();
		});
		register("echo_fork_before_villager", ctx -> {
			// a villager without a job turns away whoever right-clicks it, before an item gets a go
			var villager = EntityTypes.VILLAGER.create(ctx.getLevel(), EntitySpawnReason.COMMAND);
			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(2, 1, 2)));
			villager.snapTo(at.x, at.y, at.z, 0, 0);
			villager.setNoAi(true);
			ctx.getLevel().addFreshEntity(villager);
			var player = player(ctx, GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SilenceMobs.ECHO_FORK));
			player.connection.handleInteract(new ServerboundInteractPacket(villager.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
			ctx.assertTrue(villager.isSilent(), "the villager's own right-click went before the echo fork");
			ctx.assertTrue(!villager.isTrading(), "the villager started trading");
			ctx.succeed();
		});
		register("echo_fork_client_item_is_not_a_block", ctx -> {
			// a block item would place a ghost block on the client when the echo fork is used on one
			var fork = new ItemStack(SilenceMobs.ECHO_FORK);
			var client = ((PolymerItem) SilenceMobs.ECHO_FORK).getPolymerItem(fork, PacketContext.get());
			ctx.assertTrue(!(client instanceof BlockItem), "the echo fork shows as " + client + ", a block item");
			ctx.succeed();
		});
		register("echo_fork_recipe", ctx -> {
			var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Qol.MODID, "echo_fork"));
			ctx.assertTrue(ctx.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "there is no " + key.identifier() + " recipe");
			ctx.succeed();
		});
		register("off_echo_fork_does_nothing", ctx -> {
			var cow = cow(ctx, null);
			var fork = new ItemStack(SilenceMobs.ECHO_FORK);
			useOn(player(ctx, GameType.SURVIVAL), cow, fork);
			ctx.assertTrue(!cow.isSilent(), "the echo fork silenced the cow while silence mobs is off");
			ctx.assertTrue(fork.getCount() == 1, "the echo fork was used up");
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
