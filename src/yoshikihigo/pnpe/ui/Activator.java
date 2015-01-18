package yoshikihigo.pnpe.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.ITextEditor;
import org.osgi.framework.BundleContext;

import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "yoshikihigo.pnpe.ui"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	/**
	 * The constructor
	 */
	public Activator() {
		JavaCore.addElementChangedListener(new IElementChangedListener() {
			@Override
			public void elementChanged(ElementChangedEvent event) {
				IJavaElementDelta delta = event.getDelta();
				if (delta != null) {
					Activator.this.showCandidateMarker(event);
				}
			}
		}, ElementChangedEvent.POST_RECONCILE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext
	 * )
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in
	 * relative path
	 *
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	private void showCandidateMarker(final ElementChangedEvent event) {

		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {

				IJavaElementDelta delta = event.getDelta();
				System.out.println(delta.toString());
				// this.traverseAndPrint(delta);

				final long startTime = System.nanoTime();

				final int caretPosition = Activator.this.getCaretPosition();
				if (caretPosition < 0) {
					System.out.println("caret position is negative.");
					return;
				}

				final CompilationUnit compilationUnit = delta
						.getCompilationUnitAST();
				final List<MethodInfo> methods = new ArrayList<MethodInfo>();
				final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor("",
						compilationUnit, methods);
				compilationUnit.accept(visitor);

				final int caretLineNumber = compilationUnit
						.getLineNumber(caretPosition);
				MethodInfo targetMethod = null;
				for (final MethodInfo method : methods) {
					if (method.startLine <= caretLineNumber
							&& caretLineNumber <= method.endLine) {
						targetMethod = method;
					}
				}

				if (null != targetMethod) {
					final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
					final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
					final PDG pdg = new PDG(targetMethod, pdgNodeFactory,
							cfgNodeFactory, true, true, false,
							Integer.MAX_VALUE, Integer.MAX_VALUE,
							Integer.MAX_VALUE);
					pdg.build();
				}

				final long endTime = System.nanoTime();

				System.out.println(Long.toString((endTime - startTime) / 1000l));
			}
		});
	}

	private int getCaretPosition() {
		int caretPosition = -1;
		final IWorkbench workbench = Activator.getDefault().getWorkbench();
		final IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		if (null != window) {
			final IWorkbenchPage page = window.getActivePage();
			IEditorPart editor = page.getActiveEditor();
			if (editor instanceof ITextEditor) {
				ISelectionProvider selectionProvider = ((ITextEditor) editor)
						.getSelectionProvider();
				ISelection selection = selectionProvider.getSelection();
				if (selection instanceof ITextSelection) {
					ITextSelection textSelection = (ITextSelection) selection;
					caretPosition = textSelection.getOffset();
				}
			}
		}

		return caretPosition;
	}
}
