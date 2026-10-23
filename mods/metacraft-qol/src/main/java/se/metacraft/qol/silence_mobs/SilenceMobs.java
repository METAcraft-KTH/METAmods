package se.metacraft.qol.silence_mobs;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import se.metacraft.lib.CreativeTabNamespaces;
import se.metacraft.qol.Qol;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Silence mobs: the echo fork toggles a mob silent and back and is never used up. A name tag named
 * "silence me" silences the mob it's used on and "unsilence me" undoes it, for players used to the
 * datapack; case, spaces and underscores don't matter, and the tag is used up as naming would use
 * it. Either way the mob keeps whatever name it had. The Vanilla Tweaks datapack this replaces left every silenced mob
 * named "silenced" and silenced the nearest mob with the name, not the one clicked.
 */
public final class SilenceMobs {


	/** The echo fork: right-click a mob to silence it, again to undo it. Never used up. */
	public static final Item ECHO_FORK = register(
			"echo_fork", EchoForkItem::new,
			new Item.Properties().stacksTo(1).component(DataComponents.LORE, new ItemLore(List.of(
					Component.translatableWithFallback("item.metacraft.echo_fork.lore", "Use on a mob to silence it, again to undo it")
							.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))
			)))
	);

	private SilenceMobs() {}

	private static Item register(String id, Function<Item.Properties, Item> creator, Item.Properties properties) {
		var key = ResourceKey.create(Registries.ITEM, Qol.getID(id));
		return Registry.register(BuiltInRegistries.ITEM, key, creator.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeTabNamespaces.add(Qol.NAMESPACE);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof LivingEntity mob)) {
				return InteractionResult.PASS;
			}
			var stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof EchoForkItem) {
				// Vanilla offers the item a mob only after the mob's own right-click (a villager's
				// trades, mounting a horse) passes, so the echo fork goes first here.
				return swing(serverPlayer, hand, stack, stack.interactLivingEntity(player, mob, hand));
			}
			if (!canSilence(mob)) {
				return InteractionResult.PASS;
			}
			var silence = command(stack);
			if (silence == null) {
				return InteractionResult.PASS;
			}
			apply(serverPlayer, mob, silence);
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/**
	 * Swings the arm for a successful interaction, as vanilla's interact handling does: a non-PASS
	 * result from Fabric's callback skips that handling, swing and all.
	 */
	private static InteractionResult swing(ServerPlayer player, InteractionHand hand, ItemStack stack, InteractionResult result) {
		if (result instanceof InteractionResult.Success success && success.shouldSwing()) {
			player.swingAndResetAttackStrength(
					hand, stack.getInteractAnimation(), success.swingSource() != InteractionResult.SwingSource.PREDICTED
			);
		}
		return result;
	}

	/** Whether silence mobs is on and {@code mob} is a living mob it may silence (not a player). */
	static boolean canSilence(LivingEntity mob) {
		return !(mob instanceof Player) && mob.isAlive() && SilenceMobsConfig.getInstance().enabled();
	}

	/** True for a "silence me" tag, false for "unsilence me", null for anything else. */
	private static Boolean command(ItemStack stack) {
		var name = stack.is(Items.NAME_TAG) ? stack.get(DataComponents.CUSTOM_NAME) : null;
		if (name == null) {
			return null;
		}
		return switch (name.getString().trim().toLowerCase(Locale.ROOT).replace('_', ' ')) {
			case "silence me" -> true;
			case "unsilence me" -> false;
			default -> null;
		};
	}

	static void apply(ServerPlayer player, LivingEntity mob, boolean silence) {
		mob.setSilent(silence);
		// A puff of sparks to show it worked. Not Glowing: its outline is a post-processing pass that
		// breaks many Iris shader packs.
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY(0.6), mob.getZ(),
					16, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.08);
		}
		mob.level().playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8f, 2.0f);
		player.sendOverlayMessage(Component.translatableWithFallback(
				silence ? "qol.metacraft.silence_mobs.silenced" : "qol.metacraft.silence_mobs.unsilenced",
				silence ? "Silenced" : "Unsilenced"
		).withStyle(ChatFormatting.GRAY));
	}

}
