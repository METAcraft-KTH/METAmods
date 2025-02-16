package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.transitions.RunCommandTransition;

import java.util.stream.Stream;

public class SelectorRef implements EntityRef {

	public static final MapCodec<SelectorRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("selector").forGetter(t -> t.selectorString)
			).apply(instance, SelectorRef::new)
	);

	private final String selectorString;
	private final EntitySelector selector;

	public SelectorRef(String selectorString) {
		this.selectorString = selectorString;
		this.selector = new EntitySelectorReader(new StringReader(selectorString), true).build();
	}

	@Override
	public Stream<? extends Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		try {
			return selector.getEntities(RunCommandTransition.getSource(cutsceneInstance, false)).stream();
		} catch (CommandSyntaxException e) {
			Cutscenes.LOGGER.error(e.getMessage(), e);
			return Stream.empty();
		}
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.SELECTOR;
	}
}
