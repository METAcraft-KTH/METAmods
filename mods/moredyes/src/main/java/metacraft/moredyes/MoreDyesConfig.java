package metacraft.moredyes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import metacraft.moredyes.content.Family;
import net.minecraft.util.StringRepresentable;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code config/moredyes.json5}, editable with {@code /meta-config-screen moredyes}: how each family
 * of coloured blocks is shown to vanilla clients ({@link metacraft.moredyes.content.Looks}). Read
 * once, at startup; a change applies at the next restart.
 */
public record MoreDyesConfig(Map<String, LookChoice> looks) {

	/** A family's look: decided at startup from the room left, or forced. */
	public enum LookChoice implements StringRepresentable {
		AUTO("auto"), DONOR("donor"), DISPLAY("display");

		public static final Codec<LookChoice> CODEC = StringRepresentable.fromEnum(LookChoice::values);
		private final String name;

		LookChoice(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Every family whose look is planned at startup, deciding for itself. */
	public static final MoreDyesConfig DEFAULT = new MoreDyesConfig(defaults());

	public static final MapCodec<MoreDyesConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			CommentCodec.comment(
					Codec.unboundedMap(Codec.STRING, LookChoice.CODEC).optionalFieldOf("looks")
							.xmap(present -> present.orElse(DEFAULT.looks), Optional::of),
					"How each family of coloured blocks is shown to vanilla clients. \"auto\": decided at startup",
					"from how much room Polymer has left. \"donor\": our model on a vanilla block state, the best look,",
					"but each colour uses up states from a small pool (forcing it when the pool is too small stops the",
					"server). \"display\": an invisible block plus a display entity per placed block, which costs no",
					"states. Applies at the next restart; -Dmoredyes.look.<family> still overrides it for testing."
			).forGetter(MoreDyesConfig::looks)
	).apply(instance, MoreDyesConfig::new));

	private static final ConfigContainer<MoreDyesConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).build(MoreDyes.MOD_ID);

	public static MoreDyesConfig get() {
		return CONTAINER.get();
	}

	/** This family's look as configured; auto when the file doesn't name it. */
	public LookChoice look(Family family) {
		return looks.getOrDefault(family.id, LookChoice.AUTO);
	}

	private static Map<String, LookChoice> defaults() {
		var looks = new LinkedHashMap<String, LookChoice>();
		looks.put(Family.STAINED_GLASS_PANE.id, LookChoice.AUTO);
		return looks;
	}
}
