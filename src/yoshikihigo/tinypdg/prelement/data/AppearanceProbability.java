package yoshikihigo.tinypdg.prelement.data;

public class AppearanceProbability implements Comparable<AppearanceProbability>{

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
	
		@Override
		public int compareTo(final AppearanceProbability o) {
			if (this.support > o.support) {
				return -1;
			} else if (this.support < o.support) {
				return 1;
			} else if (this.confidence > o.confidence) {
				return -1;
			} else if (this.confidence < o.confidence) {
				return 1;
			} else {
				return 0;
			}
		}
}
