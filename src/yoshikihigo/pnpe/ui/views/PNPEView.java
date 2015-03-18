package yoshikihigo.pnpe.ui.views;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
import yoshikihigo.tinypdg.pdg.node.PDGNode;
import yoshikihigo.tinypdg.pdg.node.PDGNodeFactory;
import yoshikihigo.tinypdg.pe.MethodInfo;
import yoshikihigo.tinypdg.prelement.data.AppearanceProbability;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
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
		text = new Text(parent, SWT.MULTI);
	}

	public void createDatabase() {
		new Thread("createDatabase") {
			public void run() {
				if (null != text) {

					if (text.isDisposed()) {
						return;
					}

					text.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							text.append("STEP1: collecting Java source files from the workspace ...");
							text.append(System.getProperty("line.separator"));
						}
					});

					final IWorkspace workspace = ResourcesPlugin.getWorkspace();
					final IWorkspaceRoot root = workspace.getRoot();
					IProject[] projects = root.getProjects();
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

					if (text.isDisposed()) {
						return;
					}

					text.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							text.append("STEP2: distilling dependencies from the Java source files ...");
							text.append(System.getProperty("line.separator"));
						}
					});

					final CFGNodeFactory cfgNodeFactory = new CFGNodeFactory();
					final PDGNodeFactory pdgNodeFactory = new PDGNodeFactory();
					final ConcurrentMap<Integer, String> texts = new ConcurrentHashMap<Integer, String>();
					final ConcurrentMap<Integer, AtomicInteger> fromNodeFrequencies = new ConcurrentHashMap<Integer, AtomicInteger>();
					final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeControlFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
					final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeDataFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
					final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeExecutionFrequencies = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, AtomicInteger>>();
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

							final SortedSet<PDGNode<?>> nodes = pdg
									.getAllNodes();
							for (final PDGNode<?> fromNode : nodes) {

								// generate a hash value from fromNode
								final String fromNodeNormalizedText = Utility
										.getNormalizedText(fromNode);
								final int fromNodeHash = fromNodeNormalizedText
										.hashCode();

								// make mapping between hash value and
								// normalized text
								if (!texts.containsKey(fromNodeHash)) {
									texts.put(fromNodeHash,
											fromNodeNormalizedText);
								}

								AtomicInteger frequencies = fromNodeFrequencies
										.get(fromNodeHash);
								if (null == frequencies) {
									frequencies = new AtomicInteger(0);
									fromNodeFrequencies.put(fromNodeHash,
											frequencies);
								}
								frequencies.incrementAndGet();

								final SortedSet<PDGEdge> edges = fromNode
										.getForwardEdges();
								for (final PDGEdge edge : edges) {
									final String toNodeNormalizedText = Utility
											.getNormalizedText(edge.toNode);
									final int toNodeHash = toNodeNormalizedText
											.hashCode();
									if (edge instanceof PDGControlDependenceEdge) {
										addToNodeHash(fromNodeHash, toNodeHash,
												toNodeControlFrequencies);
									} else if (edge instanceof PDGDataDependenceEdge) {
										addToNodeHash(fromNodeHash, toNodeHash,
												toNodeDataFrequencies);
									} else if (edge instanceof PDGExecutionDependenceEdge) {
										addToNodeHash(fromNodeHash, toNodeHash,
												toNodeExecutionFrequencies);
									}
								}
							}
						}
					}

					if (text.isDisposed()) {
						return;
					}

					text.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							text.append("STEP3: registering the dependencies to SQL database ...");
							text.append(System.getProperty("line.separator"));
						}
					});

					final ConcurrentMap<Integer, List<AppearanceProbability>> frequenciesForControlDependence = new ConcurrentHashMap<Integer, List<AppearanceProbability>>();
					final ConcurrentMap<Integer, List<AppearanceProbability>> frequenciesForDataDependence = new ConcurrentHashMap<Integer, List<AppearanceProbability>>();
					final ConcurrentMap<Integer, List<AppearanceProbability>> frequenciesForExecutionDependence = new ConcurrentHashMap<Integer, List<AppearanceProbability>>();
					calculateFrequencies(DEPENDENCE_TYPE.CONTROL,
							fromNodeFrequencies, toNodeControlFrequencies,
							texts, frequenciesForControlDependence);
					calculateFrequencies(DEPENDENCE_TYPE.DATA,
							fromNodeFrequencies, toNodeDataFrequencies, texts,
							frequenciesForDataDependence);
					calculateFrequencies(DEPENDENCE_TYPE.EXECUTION,
							fromNodeFrequencies, toNodeExecutionFrequencies,
							texts, frequenciesForExecutionDependence);

					final File dbFile = new File("PNPe.database");
					if (dbFile.exists()) {
						if (!dbFile.delete()) {
							text.getDisplay().asyncExec(new Runnable() {
								@Override
								public void run() {
									text.append("Couldn't delete the old database file");
									text.append(System
											.getProperty("line.separator"));
								}
							});
						}
					}

					final DAO dao = new DAO("PNPe.database");
					registerTextsToDatabase(dao, texts);
					registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.CONTROL,
							frequenciesForControlDependence);
					registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.DATA,
							frequenciesForDataDependence);
					registerFrequenciesToDatabase(dao,
							DEPENDENCE_TYPE.EXECUTION,
							frequenciesForExecutionDependence);
					dao.close();

					if (text.isDisposed()) {
						return;
					}

					text.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							text.append("Operations were successifully finished.");
							text.append(System.getProperty("line.separator"));
						}
					});
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

	private static void addToNodeHash(
			final int fromNodeHash,
			final int toNodeHash,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeFrequencies) {

		ConcurrentMap<Integer, AtomicInteger> toNodeHashes = toNodeFrequencies
				.get(fromNodeHash);
		if (null == toNodeHashes) {
			toNodeHashes = new ConcurrentHashMap<Integer, AtomicInteger>();
			toNodeFrequencies.put(fromNodeHash, toNodeHashes);
		}
		AtomicInteger frequency = toNodeHashes.get(toNodeHash);
		if (null == frequency) {
			frequency = new AtomicInteger(0);
			toNodeHashes.put(toNodeHash, frequency);
		}
		frequency.incrementAndGet();
	}

	private static void calculateFrequencies(
			final DEPENDENCE_TYPE type,
			final ConcurrentMap<Integer, AtomicInteger> fromNodeAllFrequencies,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeAllFrequencies,
			final ConcurrentMap<Integer, String> texts,
			final ConcurrentMap<Integer, List<AppearanceProbability>> allFrequencies) {

		for (final Entry<Integer, ConcurrentMap<Integer, AtomicInteger>> entry : toNodeAllFrequencies
				.entrySet()) {
			final int fromNodeHash = entry.getKey();
			final int totalTime = fromNodeAllFrequencies.get(fromNodeHash)
					.get();
			final List<AppearanceProbability> frequencies = new ArrayList<AppearanceProbability>();
			final ConcurrentMap<Integer, AtomicInteger> toNodeFrequencies = entry
					.getValue();
			for (final Entry<Integer, AtomicInteger> entry2 : toNodeFrequencies
					.entrySet()) {
				final int toNodeHash = entry2.getKey();
				final int time = entry2.getValue().get();
				final String normalizedText = texts.get(toNodeHash);
				final AppearanceProbability frequency = new AppearanceProbability(
						type, (float) time / (float) totalTime, time,
						toNodeHash, normalizedText);
				frequencies.add(frequency);
			}
			Collections.sort(frequencies,
					new Comparator<AppearanceProbability>() {
						@Override
						public int compare(final AppearanceProbability f1,
								final AppearanceProbability f2) {
							if (f1.confidence > f2.confidence) {
								return -1;
							} else if (f1.confidence < f2.confidence) {
								return 1;
							} else {
								return 0;
							}
						}
					});
			allFrequencies.put(fromNodeHash, frequencies);
		}
	}

	private static void registerTextsToDatabase(final DAO dao,
			final ConcurrentMap<Integer, String> texts) {

		for (final Entry<Integer, String> entry : texts.entrySet()) {
			final int hash = entry.getKey();
			final String text = entry.getValue();
			dao.addToTexts(hash, text);
		}
	}

	private static void registerFrequenciesToDatabase(
			final DAO dao,
			DEPENDENCE_TYPE type,
			final ConcurrentMap<Integer, List<AppearanceProbability>> allFrequencies) {

		for (final Entry<Integer, List<AppearanceProbability>> entry : allFrequencies
				.entrySet()) {

			final int fromhash = entry.getKey();
			for (final AppearanceProbability frequency : entry.getValue()) {
				dao.addToProbabilities(type, fromhash, frequency);
			}
		}
	}

}