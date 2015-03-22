package yoshikihigo.tinypdg.prelement.data;

public class Candidate implements Comparable<Candidate>{

	public final String text;
	public final int support;
	public final float confidence;

	public Candidate(final String text, final int support,
			final float confidence) {
		this.text = text;
		this.support = support;
		this.confidence = confidence;
	}
	
	@Override
	public int compareTo(final Candidate o) {
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
