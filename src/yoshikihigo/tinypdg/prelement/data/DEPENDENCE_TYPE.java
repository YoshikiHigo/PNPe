package yoshikihigo.tinypdg.prelement.data;

public enum DEPENDENCE_TYPE {

	CONTROL("control"), DATA("data"), EXECUTION("execution");

	final public String text;

	DEPENDENCE_TYPE(final String text) {
		this.text = text;
	}

	public static DEPENDENCE_TYPE getDEPENDENCE_TYPE(final String type) {

		DEPENDENCE_TYPE instance = null;
		switch (type) {
		case "control": {
			instance = DEPENDENCE_TYPE.CONTROL;
			break;
		}
		case "data": {
			instance = DEPENDENCE_TYPE.DATA;
			break;
		}
		case "execution": {
			instance = DEPENDENCE_TYPE.EXECUTION;
			break;
		}
		}
		return instance;
	}
}
