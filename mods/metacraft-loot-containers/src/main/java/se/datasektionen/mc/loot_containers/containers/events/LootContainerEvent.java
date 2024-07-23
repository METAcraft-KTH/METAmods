package se.datasektionen.mc.loot_containers.containers.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import se.datasektionen.mc.loot_containers.METAcraftLootContainers;
import se.datasektionen.mc.loot_containers.containers.LootContainerData;

public abstract class LootContainerEvent {

	private static final NbtTextFormatter formatter = new NbtTextFormatter(" ");

	public static final Codec<LootContainerEvent> REGISTRY_CODEC = LootContainerEventRegistry.REGISTRY.getCodec().dispatch(
			LootContainerEvent::getType, LootContainerEventType::codec
	);

	private boolean removed = false;

	private Runnable markModified;

	public void initialise(Runnable markModified) {
		this.markModified = markModified;
	}

	public abstract void tick(String group, LootContainerData data, MinecraftServer server);

	public boolean shouldRemove() {
		return removed;
	}

	public void remove() {
		removed = true;
	}

	public void markModified() {
		if (markModified != null) {
			markModified.run();
		}
	}

	public abstract LootContainerEventType<?> getType();

	public LootContainerEvent copy() {
		return REGISTRY_CODEC.parse(
				JavaOps.INSTANCE,
				REGISTRY_CODEC.encodeStart(
						JavaOps.INSTANCE, this
				).resultOrPartial(METAcraftLootContainers.LOGGER::error).get()
		).resultOrPartial(METAcraftLootContainers.LOGGER::error).get();
	}

	@Override
	public String toString() {
		return REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(
				METAcraftLootContainers.LOGGER::error
		).map(NbtElement::asString).orElse("null");
	}

	public Text toText() {
		return REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(
				METAcraftLootContainers.LOGGER::error
		).map(formatter::apply).orElse(Text.literal("null"));
	}

}
