package yoshikihigo.pnpe.ui.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import yoshikihigo.tinypdg.prelement.data.Candidate;
import yoshikihigo.tinypdg.prelement.data.CandidateList;

public class JavaCompletionProposalComputer implements
		IJavaCompletionProposalComputer {

	public JavaCompletionProposalComputer() {
	}

	@Override
	public void sessionStarted() {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(
			ContentAssistInvocationContext context, IProgressMonitor monitor) {

		List<ICompletionProposal> propList = new ArrayList<ICompletionProposal>();
		int offset = context.getInvocationOffset();

		final List<Candidate> candidates = CandidateList.getInstance()
				.getCandidates();
		for (final Candidate candidate : candidates) {
			CompletionProposal proposal = new CompletionProposal(
					candidate.text, offset, 0, candidate.text.length());
			propList.add(proposal);
		}

		return propList;
	}

	@Override
	public List<IContextInformation> computeContextInformation(
			ContentAssistInvocationContext context, IProgressMonitor monitor) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getErrorMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void sessionEnded() {
		// TODO Auto-generated method stub

	}

}
