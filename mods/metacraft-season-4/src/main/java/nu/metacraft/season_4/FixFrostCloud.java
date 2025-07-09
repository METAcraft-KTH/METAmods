package nu.metacraft.season_4;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.datafixer.TypeReferences;

import java.util.Optional;

public class FixFrostCloud extends DataFix {
	public FixFrostCloud(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.writeFixAndRead("Stellarity fix frost cloud potion", this.getInputSchema().getType(TypeReferences.DATA_COMPONENTS), this.getOutputSchema().getType(TypeReferences.DATA_COMPONENTS), (dynamic) -> {
			Optional<? extends Dynamic<?>> data = dynamic.get("minecraft:custom_data").result();
			if (data.isPresent()) {
				var specialItem = data.get().get("stellarity.special_item").asString().result();
				if (specialItem.isPresent() && specialItem.get().equals("frost_cloud_potion")) {
					dynamic = dynamic.set("minecraft:potion_duration_scale", dynamic.createFloat(1));
				}
			}
			return dynamic;
		});
	}
}
