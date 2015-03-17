package yoshikihigo.pnpe.ui;

public class Candidate {

	public final String text;
	public final int support;
	public final float probability;

	public Candidate(final String text, final int support,
			final float probability) {
		this.text = text;
		this.support = support;
		this.probability = probability;
	}
}
