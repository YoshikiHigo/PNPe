package yoshikihigo.pnpe.ui.views;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import yoshikihigo.pnpe.ui.Utility;
import yoshikihigo.tinypdg.ast.TinyPDGASTVisitor;
import yoshikihigo.tinypdg.cfg.node.CFGNodeFactory;
import yoshikihigo.tinypdg.pdg.PDG;
import yoshikihigo.tinypdg.pdg.edge.PDGControlDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGDataDependenceEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGEdge;
import yoshikihigo.tinypdg.pdg.edge.PDGExecutionDependenceEdge;
import yoshikihigo.tinypdg.pdg.node.PDGMethodEnterNode;
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.prelement.data.AppearanceProbability;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Dependence;
import yoshikihigo.tinypdg.prelement.db.DAO;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class PNPEView extends ViewPart {

	public static final String ID = "yoshikihigo.pnpe.ui.views.PNPEView";

	private Text text;

	/**
	 * The constructor.
	 */
	public PNPEView() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		this.text = new Text(parent, SWT.MULTI);
	}

	public void createDatabase() {
		new Thread("createDatabase") {
			public void run() {

				if ((null == text) || text.isDisposed()) {
					return;
				}

				this.print("STEP1: collecting Java source files from the workspace ...");
				final List<ICompilationUnit> files = this.getFiles();

				if ((null == text) || text.isDisposed()) {
					return;
				}

				this.print("STEP2: distilling dependencies from the Java source files ...");
				final ConcurrentMap<String, AtomicInteger> fromNodeAppearanceNumbers = new ConcurrentHashMap<>();
				final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForControl = new ConcurrentHashMap<>();
				final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForData = new ConcurrentHashMap<>();
				final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForExecution = new ConcurrentHashMap<>();
				this.extractDependencies(files, fromNodeAppearanceNumbers,
						dependenceAppearanceNumbersForControl,
						dependenceAppearanceNumbersForData,
						dependenceAppearanceNumbersForExecution);

				if ((null == text) || text.isDisposed()) {
					return;
				}

				this.print("STEP3: registering the dependencies to SQL database ...");
				final ConcurrentMap<String, List<AppearanceProbability>> appearanceProbabilitiesForControl = new ConcurrentHashMap<>();
				final ConcurrentMap<String, List<AppearanceProbability>> appearanceProbabilitiesForData = new ConcurrentHashMap<>();
				final ConcurrentMap<String, List<AppearanceProbability>> appearanceProbabilitiesForExecution = new ConcurrentHashMap<>();
				calculateAppearanceProbabilities(DEPENDENCE_TYPE.CONTROL,
						fromNodeAppearanceNumbers,
						dependenceAppearanceNumbersForControl,
						appearanceProbabilitiesForControl);
				calculateAppearanceProbabilities(DEPENDENCE_TYPE.DATA,
						fromNodeAppearanceNumbers,
						dependenceAppearanceNumbersForData,
						appearanceProbabilitiesForData);
				calculateAppearanceProbabilities(DEPENDENCE_TYPE.EXECUTION,
						fromNodeAppearanceNumbers,
						dependenceAppearanceNumbersForExecution,
						appearanceProbabilitiesForExecution);

				if ((null == text) || text.isDisposed()) {
					return;
				}

				final File dbFile = new File("PNPe.database");
				if (dbFile.exists()) {
					if (!dbFile.delete()) {
						this.print("Couldn't delete the old database file");
					}
				}
				final DAO dao = new DAO("PNPe.database");
				registerApperanceProbabilitiesToDatabase(dao,
						appearanceProbabilitiesForControl);
				registerApperanceProbabilitiesToDatabase(dao,
						appearanceProbabilitiesForData);
				registerApperanceProbabilitiesToDatabase(dao,
						appearanceProbabilitiesForExecution);
				dao.close();

				if ((null == text) || text.isDisposed()) {
					return;
				}

				this.print("Operations were successifully finished.");
			}

			private List<ICompilationUnit> getFiles() {
				final IWorkspace workspace = ResourcesPlugin.getWorkspace();
				final IWorkspaceRoot root = workspace.getRoot();
				final IProject[] projects = root.getProjects();
				final List<ICompilationUnit> files = new ArrayList<ICompilationUnit>();
				for (IProject project : projects) {
					try {
						project.accept(new IResourceVisitor() {
							@Override
							public boolean visit(IResource resource)
									throws CoreException {
								switch (resource.getType()) {
								case IResource.FILE:
									IJavaElement file = JavaCore
											.create(resource);
									if (file instanceof ICompilationUnit) {
										files.add((ICompilationUnit) file);
									}
								}
								return true;
							}
						});
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}
				return files;
			}

			private void extractDependencies(
					final List<ICompilationUnit> files,
					final ConcurrentMap<String, AtomicInteger> fromNodeAppearanceNumbers,
					final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForControl,
					final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForData,
					final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbersForExecution) {

				final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
				final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();

				for (final ICompilationUnit file : files) {
					final ASTParser parser = ASTParser.newParser(AST.JLS8);
					parser.setSource(file);
					final ASTNode node = parser
							.createAST(new NullProgressMonitor());
					final List<MethodInfo> methods = new ArrayList<MethodInfo>();
					final TinyPDGASTVisitor visitor = new TinyPDGASTVisitor(
							file.getPath().toFile().getAbsolutePath(),
							(CompilationUnit) node, methods);
					node.accept(visitor);

					for (final MethodInfo method : methods) {
						final PDG pdg = new PDG(method, pdgNodeFactory,
								cfgNodeFactory, true, true, false,
								Integer.MAX_VALUE, Integer.MAX_VALUE,
								Integer.MAX_VALUE);
						pdg.build();

						final SortedSet<PDGNode<?>> nodes = pdg.getAllNodes();
						for (final PDGNode<?> fromNode : nodes) {

							if(fromNode instanceof PDGMethodEnterNode){
								continue;
							}
							
							if (fromNode.getForwardEdges().isEmpty()) {
								continue;
							}

							final Map<String, String> absoluteNormalizationMap = new HashMap<>();
							final String fromNodeAbsoluteNormalizationText = Utility
									.getNormalizedText(fromNode,
											absoluteNormalizationMap);

							AtomicInteger fromNodeAppearanceNumber = fromNodeAppearanceNumbers
									.get(fromNodeAbsoluteNormalizationText);
							if (null == fromNodeAppearanceNumber) {
								fromNodeAppearanceNumber = new AtomicInteger(0);
								fromNodeAppearanceNumbers.put(
										fromNodeAbsoluteNormalizationText,
										fromNodeAppearanceNumber);
							}
							fromNodeAppearanceNumber.incrementAndGet();

							final SortedSet<PDGEdge> forwardEdges = fromNode
									.getForwardEdges();
							for (final PDGEdge edge : forwardEdges) {

								final PDGNode<?> toNode = edge.toNode;

								final Map<String, String> relativeNormalizationMap = new HashMap<>();
								final String toNodeRelativeNormalizationText = Utility
										.getNormalizedText(toNode,
												relativeNormalizationMap);
								final String fromNodeRelativeNormalizationText = Utility
										.getNormalizedText(fromNode,
												relativeNormalizationMap);

								final String absoluteRelativeMap = makeMap(
										absoluteNormalizationMap,
										relativeNormalizationMap);

								final Dependence dependence = new Dependence(
										fromNodeAbsoluteNormalizationText,
										fromNodeRelativeNormalizationText,
										toNodeRelativeNormalizationText,
										absoluteRelativeMap);

								if (edge instanceof PDGControlDependenceEdge) {
									addDependence(dependence,
											dependenceAppearanceNumbersForControl);
								} else if (edge instanceof PDGDataDependenceEdge) {
									addDependence(dependence,
											dependenceAppearanceNumbersForData);
								} else if (edge instanceof PDGExecutionDependenceEdge) {
									addDependence(dependence,
											dependenceAppearanceNumbersForExecution);
								}

							}
						}
					}
				}
			}

			private void addDependence(
					final Dependence dependence,
					final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbers) {
				AtomicInteger appearanceNumber = dependenceAppearanceNumbers
						.get(dependence);
				if (null == appearanceNumber) {
					appearanceNumber = new AtomicInteger(0);
					dependenceAppearanceNumbers.putIfAbsent(dependence,
							appearanceNumber);
				}
				appearanceNumber.incrementAndGet();
			}

			private String makeMap(
					final Map<String, String> absoluteNormalizationMap,
					final Map<String, String> relativeNormalizationMap) {
				final StringBuffer buffer = new StringBuffer();
				for (final Entry<String, String> absoluteEntry : absoluteNormalizationMap
						.entrySet()) {
					final String originalName = absoluteEntry.getKey();
					final String absoluteNormalizedName = absoluteEntry
							.getValue();
					final String relativeNormalizedName = relativeNormalizationMap
							.get(originalName);
					buffer.append(absoluteNormalizedName);
					buffer.append(":");
					buffer.append(relativeNormalizedName);
					buffer.append(",");
				}
				return buffer.toString();
			}

			private void calculateAppearanceProbabilities(
					final DEPENDENCE_TYPE type,
					final ConcurrentMap<String, AtomicInteger> fromNodeAppearanceNumbers,
					final ConcurrentMap<Dependence, AtomicInteger> dependenceAppearanceNumbers,
					final ConcurrentMap<String, List<AppearanceProbability>> appearanceProbabilities) {

				for (final Entry<Dependence, AtomicInteger> entry : dependenceAppearanceNumbers
						.entrySet()) {
					final Dependence dependence = entry.getKey();
					final int appearanceNumber = entry.getValue().get();

					final String fromNodeAbsoluteNormalizationText = dependence.fromNodeAbsoluteNormalizationText;
					final int fromNodeAppearanceNumber = fromNodeAppearanceNumbers
							.get(fromNodeAbsoluteNormalizationText).get();

					final AppearanceProbability probability = new AppearanceProbability(
							type, dependence, (float) appearanceNumber
									/ (float) fromNodeAppearanceNumber,
							appearanceNumber);
					List<AppearanceProbability> probabilities = appearanceProbabilities
							.get(fromNodeAbsoluteNormalizationText);
					if (null == probabilities) {
						probabilities = new ArrayList<>();
						appearanceProbabilities.put(
								fromNodeAbsoluteNormalizationText,
								probabilities);
					}
					probabilities.add(probability);
				}

				for (final List<AppearanceProbability> probabilities : appearanceProbabilities
						.values()) {
					Collections.sort(probabilities,
							new Comparator<AppearanceProbability>() {
								@Override
								public int compare(
										final AppearanceProbability f1,
										final AppearanceProbability f2) {
									if (f1.confidence > f2.confidence) {
										return -1;
									} else if (f1.confidence < f2.confidence) {
										return 1;
									} else if (f1.support > f2.support) {
										return -1;
									} else if (f1.support < f2.support) {
										return 1;
									} else {
										return 0;
									}
								}
							});
				}
			}

			private void print(final String str) {
				text.getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						text.append(str);
						text.append(System.getProperty("line.separator"));
					}
				});
			}

			private void registerApperanceProbabilitiesToDatabase(
					final DAO dao,
					final ConcurrentMap<String, List<AppearanceProbability>> allProbabilities) {

				for (final List<AppearanceProbability> probabilities : allProbabilities
						.values()) {
					for (final AppearanceProbability probability : probabilities) {
						dao.addToProbabilities(probability);
					}
				}
			}
		}.start();
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		text.setFocus();
	}
}