package nu.metacraft.bundles.extensions;

import org.apache.commons.lang3.math.Fraction;

public interface BundlesComponentExtensions {

	Fraction METAcraft_Fixes$getBundleSizeFactor();

	interface Internal extends BundlesComponentExtensions {
		void METAcraft_Fixes$setBundleSizeFactor(Fraction factor);
	}

}
