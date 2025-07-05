package nu.metacraft.loot_containers.containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.loot_containers.METAcraftLootContainers;
import nu.metacraft.loot_containers.util.PosOrUUID;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class LootContainer {

	private static final NbtTextFormatter formatter = new NbtTextFormatter(" ");

	public static final Codec<LootContainer> REGISTRY_CODEC = LootContainerRegistry.REGISTRY.getCodec().dispatch(
			LootContainer::getType, LootContainerType::codec
	);

	protected ServerWorld world;

	protected Supplier<Optional<LootAccess>> access;
	protected PosOrUUID pos;
	private Runnable markDirty;


	public void initialise(ServerWorld world, Supplier<Optional<LootAccess>> access, PosOrUUID pos, Runnable markDirty) {
		this.world = world;
		this.access = access;
		this.pos = pos;
		this.markDirty = markDirty;
	}

	public abstract void onOpen(@Nullable ServerPlayerEntity player);

	public abstract LootContainerType<? extends LootContainer> getType();

	public Runnable getTicker() {
		return null;
	}

	public void markDirty() {
		if (markDirty != null) {
			markDirty.run();
		}
	}

	public ServerWorld getWorld() {
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

	public Text toText() {
		return REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(
				METAcraftLootContainers.LOGGER::error
		).map(formatter::apply).orElse(Text.literal("null"));
	}
}
