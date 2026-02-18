package nu.metacraft.bundles.extensions;

import org.apache.commons.lang3.math.Fraction;

public interface BundlesComponentExtensions {

	Fraction metacraft_bundles$getBundleSizeFactor();

	interface Internal extends BundlesComponentExtensions {
		void metacraft_bundles$setBundleSizeFactor(Fraction factor);
	}

}
