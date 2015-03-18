package yoshikihigo.tinypdg.prelement.data;

public class AppearanceProbability {

	public final DEPENDENCE_TYPE type;
	public final float confidence;
	public final int support;
	public final int hash;
	public final String text;

	public AppearanceProbability(final DEPENDENCE_TYPE type,
			final float confidence, final int support, final int hash,
			final String text) {
		this.type = type;
		this.confidence = confidence;
		this.support = support;
		this.hash = hash;
		this.text = text;
	}
}
