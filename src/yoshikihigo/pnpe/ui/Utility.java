package yoshikihigo.pnpe.ui;

import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

public class Utility {

	public static String getNormalizedText(final PDGNode<?> node) {
		final NormalizedText fromNodeNormalizedText1 = new NormalizedText(
				node.core);
		final String fromNodeNormalizedText2 = NormalizedText
				.normalize(fromNodeNormalizedText1.getText());
		return fromNodeNormalizedText2;
	}
	
}
