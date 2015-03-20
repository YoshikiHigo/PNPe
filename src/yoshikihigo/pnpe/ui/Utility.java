package yoshikihigo.pnpe.ui;

import java.util.HashMap;
import java.util.Map;

import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

public class Utility {

	public static String getNormalizedText(final PDGNode<?> node) {
		return getNormalizedText(node, new HashMap<String, String>());
	}

	public static String getNormalizedText(final PDGNode<?> node,
			final Map<String, String> normalizedMap) {
		final NormalizedText normalizedText = new NormalizedText(node.core);
		final String text = NormalizedText.normalize(normalizedText.getText(),
				normalizedMap);
		return text;
	}
}
