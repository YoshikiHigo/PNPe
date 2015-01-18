package yoshikihigo.pnpe.ui.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

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

		String str;
		CompletionProposal proposal;

		str = "akisute";
		proposal = new CompletionProposal(str, offset, 10, str.length());
		// propList.add(proposal);
		propList.add(0, proposal);
		str = "kashitsune";
		proposal = new CompletionProposal(str, offset, 0, str.length());
		propList.add(proposal);

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
