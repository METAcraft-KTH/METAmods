package nu.metacraft.loot_containers.containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.loot_containers.METAcraftLootContainers;
import nu.metacraft.loot_containers.util.PosOrUUID;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class LootContainer {

	private static final TextComponentTagVisitor formatter = new TextComponentTagVisitor(" ");

	public static final Codec<LootContainer> REGISTRY_CODEC = LootContainerRegistry.REGISTRY.byNameCodec().dispatch(
			LootContainer::getType, LootContainerType::codec
	);

	protected ServerLevel world;

	protected Supplier<Optional<LootAccess>> access;
	protected PosOrUUID pos;
	private Runnable markDirty;


	public void initialise(ServerLevel world, Supplier<Optional<LootAccess>> access, PosOrUUID pos, Runnable markDirty) {
		this.world = world;
		this.access = access;
		this.pos = pos;
		this.markDirty = markDirty;
	}

	public abstract void onOpen(@Nullable ServerPlayer player);

	public abstract LootContainerType<? extends LootContainer> getType();

	public Runnable getTicker() {
		return null;
	}

	public void markDirty() {
		if (markDirty != null) {
			markDirty.run();
		}
	}

	public ServerLevel getWorld() {
		return world;
	}

	public PosOrUUID getPos() {
		return pos;
	}

	public LootContainer copy() {
		return REGISTRY_CODEC.parse(
				JavaOps.INSTANCE,
				REGISTRY_CODEC.encodeStart(
						JavaOps.INSTANCE, this
				).resultOrPartial(METAcraftLootContainers.LOGGER::error).get()
		).resultOrPartial(METAcraftLootContainers.LOGGER::error).get();
	}

	public Component toText() {
		return REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(
				METAcraftLootContainers.LOGGER::error
		).map(formatter::visit).orElse(Component.literal("null"));
	}
}
