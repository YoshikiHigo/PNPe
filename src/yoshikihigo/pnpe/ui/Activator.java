package yoshikihigo.pnpe.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

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
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.prelement.data.AppearanceProbability;
import yoshikihigo.tinypdg.prelement.db.DAO;

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

				final DAO dao = new DAO("PNPe.database");

				if (null != targetMethod) {
					final PDG pdg = Activator.this.buildPDG(targetMethod);
					final Map<Integer, List<AppearanceProbability>> allAppearanceProbabilities = new HashMap<>();
					final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
					for (final PDGNode<?> fromNode : nodes) {
						final String fromNodeNormalizedText = Utility
								.getNormalizedText(fromNode);
						final int fromNodeHash = fromNodeNormalizedText
								.hashCode();

						final List<AppearanceProbability> appearanceProbabilities = dao
								.getAppearanceFrequencies(fromNodeHash);

						final Set<Integer> toNodeHashes = new HashSet<>();
						for (final PDGEdge edge : fromNode.getForwardEdges()) {
							final Map<String, String> toNodeNormalizationMap = new HashMap<>();
							final String toNomalizedText = Utility
									.getNormalizedText(edge.toNode,
											toNodeNormalizationMap);
							toNodeHashes.add(toNomalizedText.hashCode());
						}

						for (final AppearanceProbability appearanceProbability : appearanceProbabilities) {
							if (toNodeHashes
									.contains(appearanceProbability.hash)) {
								continue;
							}
							List<AppearanceProbability> list = allAppearanceProbabilities
									.get(appearanceProbability.hash);
							if (null == list) {
								list = new ArrayList<>();
								allAppearanceProbabilities.put(
										appearanceProbability.hash, list);
							}
							list.add(appearanceProbability);
						}

					}
					final List<List<AppearanceProbability>> freqList = new ArrayList<List<AppearanceProbability>>();
					freqList.addAll(allAppearanceProbabilities.values());
					Collections.sort(freqList,
							new Comparator<List<AppearanceProbability>>() {
								@Override
								public int compare(
										final List<AppearanceProbability> o1,
										final List<AppearanceProbability> o2) {
									int support1 = 0;
									int support2 = 0;
									float probability1 = 0;
									float probability2 = 0;
									for (final AppearanceProbability f : o1) {
										support1 += f.support;
										probability1 += f.confidence;
									}
									for (final AppearanceProbability f : o2) {
										support2 += f.support;
										probability2 += f.confidence;
									}

									if (support1 > support2) {
										return -1;
									} else if (support1 < support2) {
										return 1;
									} else if (probability1 > probability2) {
										return -1;
									} else if (probability1 < probability2) {
										return 1;
									} else {
										return 0;
									}
								}
							});

					for (final List<AppearanceProbability> freq : freqList) {
						final Candidate c = makeCandidate(freq,
								new HashMap<String, String>());
						CandidateList.getInstance().add(c);
					}

					System.out.println("there are " + freqList.size()
							+ " candidates.");
				}

				dao.close();

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

	private PDG buildPDG(final MethodInfo method) {
		final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
		final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
		final PDG pdg = new PDG(method, pdgNodeFactory, cfgNodeFactory, true,
				true, false, Integer.MAX_VALUE, Integer.MAX_VALUE,
				Integer.MAX_VALUE);
		pdg.build();
		return pdg;
	}

	private Candidate makeCandidate(
			final List<AppearanceProbability> appearanceProbabilities,
			final Map<String, String> normalizationMap) {
		int support = 0;
		float confidence = 0;
		for (final AppearanceProbability probability : appearanceProbabilities) {
			support += probability.support;
			confidence += probability.confidence;
		}
		final StringBuilder text = new StringBuilder(
				appearanceProbabilities.get(0).text);
		for (final Entry<String, String> entry : normalizationMap.entrySet()) {
			final String originalName = entry.getKey();
			final String normalizedName = entry.getValue();
			int index = 0;
			while (0 < (index = text.indexOf(normalizedName))) {
				text.replace(index, index + normalizedName.length(),
						originalName);
			}
		}
		return new Candidate(text.toString(), support, confidence);
	}
}
