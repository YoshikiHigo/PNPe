package yoshikihigo.pnpe.ui;

import java.util.ArrayList;
import java.util.List;

public class CandidateList {

	static private CandidateList SINGLETON = null;

	static public synchronized CandidateList getInstance() {
		if (null == SINGLETON) {
			SINGLETON = new CandidateList();
		}
		return SINGLETON;
	}

	final private List<Candidate> candidates;

	private CandidateList() {
		this.candidates = new ArrayList<>();
	}

	public boolean add(final Candidate candidate) {
		if (null != candidate) {
			return this.candidates.add(candidate);
		}
		return false;
	}

	public List<Candidate> getCandidates() {
		final List<Candidate> candidates = new ArrayList<>();
		candidates.addAll(this.candidates);
		return candidates;
	}
}
