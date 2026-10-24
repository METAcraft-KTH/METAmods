package se.metacraft.kultur.drinks;

import se.metacraft.kultur.Kultur;
import net.minecraft.resources.Identifier;

/**
 * PolymITer's four drinks, with the alcohol each adds (the values in Kultur's Brewery drinks). Three
 * are potions from the brewing stand ({@link DrinkPotions}); Nyckeln, the sober one, is crafted
 * ({@link NyckelnItem}).
 */
public enum DrinkKind {
	ALCOHOL("alcohol", 20, "Alcoholic Beverage"),
	SPIKEN("spiken", 30, "Spiken Patch Drink"),
	SLAGGAN("slaggan", 35, "Släggan Patch Drink"),
	NYCKELN("nyckeln", 0, "Nyckeln Patch Drink");

	private final String id;
	private final double alcohol;
	private final String displayName;

	DrinkKind(String id, double alcohol, String displayName) {
		this.id = id;
		this.alcohol = alcohol;
		this.displayName = displayName;
	}

	public String id() {
		return this.id;
	}

	public double alcohol() {
		return this.alcohol;
	}

	public String displayName() {
		return this.displayName;
	}

	/** The bottle a client draws, PolymITer's model: {@code assets/kultur/items/drink/<id>.json}. */
	public Identifier model() {
		return Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "drink/" + this.id);
	}

	/**
	 * The potion's name, which a vanilla client names its bottles and tipped arrows by:
	 * {@code item.minecraft.potion.effect.kultur_spiken} and so on, in Kultur's lang.
	 */
	public String potionName() {
		return Kultur.NAMESPACE + "_" + this.id;
	}
}
