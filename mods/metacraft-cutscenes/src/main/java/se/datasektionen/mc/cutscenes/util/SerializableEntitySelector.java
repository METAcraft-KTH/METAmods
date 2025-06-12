package se.datasektionen.mc.cutscenes.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;

public class SerializableEntitySelector {

	public static final Codec<SerializableEntitySelector> CODEC = Codec.STRING.comapFlatMap(
			SerializableEntitySelector::create, selector -> selector.selectorString
	);

	protected final String selectorString;
	protected final EntitySelector selector;

	protected SerializableEntitySelector(String selector) throws CommandSyntaxException {
		this.selectorString = selector;
		this.selector = new EntitySelectorReader(new StringReader(selectorString), true).read();
	}

	public static DataResult<SerializableEntitySelector> create(String selector) {
		try {
			return DataResult.success(new SerializableEntitySelector(selector));
		} catch (CommandSyntaxException e) {
			return DataResult.error(e::getMessage);
		}
	}

	public EntitySelector get() {
		return selector;
	}

}
