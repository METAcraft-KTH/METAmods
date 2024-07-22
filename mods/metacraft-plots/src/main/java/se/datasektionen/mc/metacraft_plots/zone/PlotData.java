package se.datasektionen.mc.metacraft_plots.zone;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import se.datasektionen.mc.zones.zone.data.ZoneData;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

import java.util.*;

public class PlotData extends ZoneData {

	private String plotSecret;
	private final BiMap<String, String> secondarySecrets;

	public static final MapCodec<PlotData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("plotSecret").forGetter(data -> data.plotSecret),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("secondarySecrets").forGetter(
					data -> data.secondarySecrets
			)
	).apply(instance, PlotData::new));

	public PlotData() {
		this(generateSecret(), new HashMap<>());
	}

	public PlotData(String plotSecret, Map<String, String> secondarySecrets) {
		this.plotSecret = plotSecret;
		this.secondarySecrets = HashBiMap.create(secondarySecrets);
	}

	public String getPlotSecret() {
		return plotSecret;
	}

	public Collection<String> getSecondaryKeyNames() {
		return secondarySecrets.keySet();
	}

	public boolean checkSecret(String secret) {
		return secret.equals(plotSecret) || secondarySecrets.inverse().containsKey(secret);
	}

	public void regeneratePlotSecret() {
		plotSecret = generateSecret();
		markDirty();
	}

	public boolean friendlyNameIsOccupied(String friendlyName) {
		return secondarySecrets.containsKey(friendlyName);
	}

	public Optional<String> addSecondarySecretWithFriendlyName(String friendlyName) {
		if (secondarySecrets.containsKey(friendlyName)) {
			return Optional.empty();
		}
		var secret = generateSecret();
		while (secondarySecrets.inverse().containsKey(secret)) {
			secret = generateSecret();
		}
		secondarySecrets.put(friendlyName, secret);
		markDirty();
		return Optional.of(secret);
	}

	public SecondarySecret addSecondarySecret() {
		var friendlyName = UUID.randomUUID().toString();
		while (secondarySecrets.containsKey(friendlyName)) {
			friendlyName = UUID.randomUUID().toString();
		}
		return new SecondarySecret(friendlyName, addSecondarySecretWithFriendlyName(friendlyName).get());
	}

	public record SecondarySecret(String friendlyName, String secret) {}

	public boolean revokeSecondarySecret(String friendlyName) {
		var value = secondarySecrets.remove(friendlyName);
		markDirty();
		return value != null;
	}

	public void revokeAllSecondarySecrets() {
		secondarySecrets.clear();
		markDirty();
	}

	private static String generateSecret() {
		return RandomStringUtils.randomAscii(50);
	}

	@Override
	public ZoneDataType<PlotData> getType() {
		return PlotDataTypes.PLOT;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
