package yoshikihigo.tinypdg.prelement.data;

public class AppearanceProbability {

	public final DEPENDENCE_TYPE type;
	public final Dependence dependence;
	public final float confidence;
	public final int support;

	public AppearanceProbability(final DEPENDENCE_TYPE type, final Dependence dependence,
			final float confidence, final int support) {
		this.type = type;
		this.dependence = dependence;
		this.confidence = confidence;
		this.support = support;
	}
}
