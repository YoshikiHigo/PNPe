package yoshikihigo.tinypdg.prelement.data;

public class Dependence {

	public final String fromNodeAbsoluteNormalizationText;
	public final String fromNodeRelativeNormalizationText;
	public final String toNodeNormalizationText;
	public final String absoluteRelativeMap;

	public Dependence(final String fromNodeAbsoluteNormalizationText,
			final String fromNodeRelativeNormalizationText,
			final String toNodeNormalizationText, final String absoluteRelativeMap) {

		this.fromNodeAbsoluteNormalizationText = fromNodeAbsoluteNormalizationText;
		this.fromNodeRelativeNormalizationText = fromNodeRelativeNormalizationText;
		this.toNodeNormalizationText = toNodeNormalizationText;
		this.absoluteRelativeMap = absoluteRelativeMap;
	}

	@Override
	public int hashCode() {
		return this.fromNodeAbsoluteNormalizationText.hashCode()
				+ this.toNodeNormalizationText.hashCode();
	}

	@Override
	public boolean equals(final Object o) {

		if (!(o instanceof Dependence)) {
			return false;
		}

		final Dependence target = (Dependence) o;
		return this.fromNodeAbsoluteNormalizationText
				.equals(target.fromNodeAbsoluteNormalizationText)
				&& this.toNodeNormalizationText
						.equals(target.toNodeNormalizationText);
	}
}
