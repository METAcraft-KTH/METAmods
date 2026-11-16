package se.metacraft.kultur.gametest;

import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import se.metacraft.kultur.Kultur;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The loom's result slot, as the packet that carries it to a client. A banner from the loom holds
 * the pattern holder the pattern item's {@code provides_banner_patterns} gave it, and the packet
 * writes that holder as an id in the server's banner pattern registry — so the holder must be one
 * of the server's. It is on a real server; it is not once a client in the same JVM has bound the
 * shared item components to its own registries, which is what the client test hit.
 */
public final class KulturLoomPacketTests {
	/** Every banner a vanilla client can be sent: from the loom with each pattern item, and built from the registry. */
	@GameTest
	public void loomResultsAndBannersEncode(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> wrong = new ArrayList<>();
		Map<String, Item> items = patternItems();
		items.forEach((name, item) -> {
			String error = encodeLoomResult(helper, item);
			if (error != null) wrong.add(name + " from the loom: " + error);
		});
		Registry<BannerPattern> registry = server.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			String id = owned.value().id();
			ItemStack banner = new ItemStack(Items.BANNER.white());
			banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
					.add(registry.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BANNER_PATTERN,
							Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, id))), DyeColor.BLACK)
					.build());
			String error = encode(server.registryAccess(), banner);
			if (error != null) wrong.add("banner with " + id + ": " + error);
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The client test's crash, on a server alone: bind the item components the way a client does on
	 * joining over the network ({@code RegistryDataCollector.collectGameRegistries} with a connection
	 * that is not in memory), against a banner pattern registry of the client's own objects. Every
	 * pattern item — vanilla's flower as much as Kultur's — then puts a foreign holder in the loom
	 * result, which the server cannot write. Binds the server's components back before it returns.
	 */
	@GameTest
	public void itemsBoundToAnotherRegistryBreakTheLoom(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> wrong = new ArrayList<>();
		Map<String, Item> items = patternItems();
		try {
			BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(clientLike(server.registryAccess()))
					.forEach(DataComponentInitializers.PendingComponents::apply);
			items.forEach((name, item) -> {
				String error = encodeLoomResult(helper, item);
				if (error == null || !error.contains("Can't find id for")) wrong.add(name + ": encoded (" + error + ")");
				else if (name.equals("arkitektur")) Kultur.LOGGER.info("[{}] bound to another registry, arkitektur from the loom: {}", Kultur.MOD_ID, error);
			});
		} finally {
			BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(server.reloadableRegistries().lookup())
					.forEach(DataComponentInitializers.PendingComponents::apply);
		}
		items.forEach((name, item) -> {
			String error = encodeLoomResult(helper, item);
			if (error != null) wrong.add(name + " after binding back: " + error);
		});
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** Kultur's pattern items by pattern id, and vanilla's flower pattern as the control. */
	private static Map<String, Item> patternItems() {
		Map<String, Item> items = new LinkedHashMap<>();
		ModContent.items().forEach(items::put);
		items.put("minecraft:flower_banner_pattern", Items.FLOWER_BANNER_PATTERN);
		return items;
	}

	/** Banner, dye and the pattern item in a loom; the result slot's packet written, or why it could not be. */
	private static String encodeLoomResult(GameTestHelper helper, Item patternItem) {
		LoomMenu loom = new LoomMenu(0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory());
		loom.getBannerSlot().set(new ItemStack(Items.BANNER.white()));
		loom.getDyeSlot().set(new ItemStack(Items.DYE.black()));
		loom.getPatternSlot().set(new ItemStack(patternItem));
		ItemStack result = loom.getResultSlot().getItem();
		if (result.isEmpty()) return "empty result (" + loom.getSelectablePatterns().size() + " selectable)";
		return encode(helper.getLevel().registryAccess(), result);
	}

	private static String encode(RegistryAccess access, ItemStack stack) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
		try {
			ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buf, new ClientboundContainerSetSlotPacket(1, 0, 3, stack));
			return null;
		} catch (RuntimeException e) {
			Throwable cause = e;
			while (cause.getCause() != null) cause = cause.getCause();
			return cause.getClass().getSimpleName() + ": " + cause.getMessage();
		} finally {
			buf.release();
		}
	}

	/** The server's registries, but banner patterns as a client builds them: equal values, its own objects. */
	private static HolderLookup.Provider clientLike(RegistryAccess server) {
		Registry<BannerPattern> original = server.lookupOrThrow(Registries.BANNER_PATTERN);
		MappedRegistry<BannerPattern> copy = new MappedRegistry<>(Registries.BANNER_PATTERN, Lifecycle.stable());
		original.listElements().forEach(h -> copy.register(h.key(),
				new BannerPattern(h.value().assetId(), h.value().translationKey()), RegistrationInfo.BUILT_IN));
		Map<TagKey<BannerPattern>, List<Holder<BannerPattern>>> tags = new HashMap<>();
		original.getTags().forEach(named -> tags.put(named.key(), named.stream()
				.<Holder<BannerPattern>>map(h -> copy.getOrThrow(h.unwrapKey().orElseThrow())).toList()));
		copy.bindTags(tags);
		copy.freeze();
		Stream<HolderLookup.RegistryLookup<?>> lookups = server.registries()
				.map(e -> e.key().equals(Registries.BANNER_PATTERN) ? copy : e.value());
		return HolderLookup.Provider.create(lookups);
	}
}
