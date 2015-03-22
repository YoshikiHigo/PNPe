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
import java.util.StringTokenizer;

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
import yoshikihigo.tinypdg.prelement.data.Candidate;
import yoshikihigo.tinypdg.prelement.data.CandidateList;
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

				final long startTime = System.nanoTime();

				final MethodInfo targetMethod = this.getTargetMethod();

				if (null != targetMethod) {

					final DAO dao = new DAO("PNPe.database");
					final PDG pdg = this.buildPDG(targetMethod);
					final Map<String, List<AppearanceProbability>> allAppearanceProbabilities = new HashMap<>();
					final Map<AppearanceProbability, Map<String, String>> probabilityNormalizationMap = new HashMap<>();
					final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
					for (final PDGNode<?> fromNode : nodes) {

						final Map<String, String> fromNodeNormalizationMap = new HashMap<>();
						final String fromNodeNormalizedText = Utility
								.getNormalizedText(fromNode,
										fromNodeNormalizationMap);
						final List<AppearanceProbability> appearanceProbabilities = dao
								.getAppearanceFrequencies(fromNodeNormalizedText);

						final Set<String> existingNodes = new HashSet<>();
						for (final PDGEdge edge : fromNode.getForwardEdges()) {
							final String toNomalizedText = Utility
									.getNormalizedText(edge.toNode);
							existingNodes.add(toNomalizedText);
						}

						for (final AppearanceProbability probability : appearanceProbabilities) {

							if (existingNodes.contains(probability)) {
								continue;
							}

							final String toNodeText = probability.dependence.toNodeNormalizationText;
							List<AppearanceProbability> probabilities = allAppearanceProbabilities
									.get(toNodeText);
							if (null == probabilities) {
								probabilities = new ArrayList<>();
								allAppearanceProbabilities.put(toNodeText,
										probabilities);
							}
							probabilities.add(probability);
							probabilityNormalizationMap.put(probability,
									fromNodeNormalizationMap);
						}
					}

					for (final List<AppearanceProbability> probabilities : allAppearanceProbabilities
							.values()) {
						Collections.sort(probabilities);
					}
					final List<Candidate> candidates = new ArrayList<>();
					for (final Entry<String, List<AppearanceProbability>> entry : allAppearanceProbabilities
							.entrySet()) {
						final String toNodeNormalizedText = entry.getKey();
						final List<AppearanceProbability> probabilities = entry
								.getValue();
						final Candidate candidate = makeCandidate(
								toNodeNormalizedText, probabilities,
								probabilityNormalizationMap);
						candidates.add(candidate);
					}
					Collections.sort(candidates);
					CandidateList.getInstance().addAll(candidates);

					System.out.println("there are " + candidates.size()
							+ " candidates.");

					dao.close();
				}

				final long endTime = System.nanoTime();

				System.out.println(Long.toString((endTime - startTime) / 1000l));
			}

			private MethodInfo getTargetMethod() {

				IJavaElementDelta delta = event.getDelta();
				// System.out.println(delta.toString());
				// this.traverseAndPrint(delta);

				final int caretPosition = this.getCaretPosition();
				if (caretPosition < 0) {
					System.out.println("caret position is negative.");
					return null;
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

				return targetMethod;
			}

			private PDG buildPDG(final MethodInfo method) {
				final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
				final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
				final PDG pdg = new PDG(method, pdgNodeFactory, cfgNodeFactory,
						true, true, false, Integer.MAX_VALUE,
						Integer.MAX_VALUE, Integer.MAX_VALUE);
				pdg.build();
				return pdg;
			}

			private int getCaretPosition() {
				int caretPosition = -1;
				final IWorkbench workbench = Activator.getDefault()
						.getWorkbench();
				final IWorkbenchWindow window = workbench
						.getActiveWorkbenchWindow();
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

			private Candidate makeCandidate(
					final String normalizedText,
					final List<AppearanceProbability> appearanceProbabilities,
					final Map<AppearanceProbability, Map<String, String>> probabilityNormalizationMap) {

				final StringBuffer text = new StringBuffer(normalizedText);
				int support = 0;
				float confidence = 0f;

				for (final AppearanceProbability p : appearanceProbabilities) {

					support += p.support;
					confidence += p.confidence;

					final Map<String, String> absoluteRelativeNormalizationMap = getAbsoluteRelativeNormalizationMap(p.dependence.absoluteRelativeMap);
					final Map<String, String> normalizationMap = probabilityNormalizationMap
							.get(p);
					for (final Entry<String, String> entry : normalizationMap
							.entrySet()) {
						final String originalName = entry.getKey();
						final String absoluteNormalizedName = entry.getValue();
						final String relativeNormalizedName = absoluteRelativeNormalizationMap
								.get(absoluteNormalizedName);

						int index = 0;
						while (-1 < (index = text
								.indexOf(relativeNormalizedName))) {
							text.replace(index,
									index + relativeNormalizedName.length(),
									originalName);
						}
					}
				}

				return new Candidate(text.toString(), support, confidence);
			}

			private Map<String, String> getAbsoluteRelativeNormalizationMap(
					final String text) {
				final Map<String, String> map = new HashMap<>();
				final StringTokenizer tokenizer = new StringTokenizer(text, ",");
				while (tokenizer.hasMoreTokens()) {
					final String token = tokenizer.nextToken();
					final int delimiterIndex = token.indexOf(":");
					final String absoluteNormalizedText = token.substring(0,
							delimiterIndex);
					final String relativeNormalizedText = token
							.substring(delimiterIndex + 1);
					map.put(absoluteNormalizedText, relativeNormalizedText);
				}
				return map;
			}
		});
	}

}
