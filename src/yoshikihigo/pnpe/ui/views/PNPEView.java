package yoshikihigo.pnpe.ui.views;

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
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;
import yoshikihigo.tinypdg.prelement.data.Frequency;
import yoshikihigo.tinypdg.prelement.db.DAO;
import yoshikihigo.tinypdg.scorpio.NormalizedText;

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
								final String fromNodeNormalizedText = getNormalizedText(fromNode);
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
									final String toNodeNormalizedText = getNormalizedText(edge.toNode);
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
					
					final ConcurrentMap<Integer, List<Frequency>> frequenciesForControlDependence = new ConcurrentHashMap<Integer, List<Frequency>>();
					final ConcurrentMap<Integer, List<Frequency>> frequenciesForDataDependence = new ConcurrentHashMap<Integer, List<Frequency>>();
					final ConcurrentMap<Integer, List<Frequency>> frequenciesForExecutionDependence = new ConcurrentHashMap<Integer, List<Frequency>>();
					calculateFrequencies(fromNodeFrequencies, toNodeControlFrequencies,
							texts, frequenciesForControlDependence);
					calculateFrequencies(fromNodeFrequencies, toNodeDataFrequencies,
							texts, frequenciesForDataDependence);
					calculateFrequencies(fromNodeFrequencies,
							toNodeExecutionFrequencies, texts,
							frequenciesForExecutionDependence);

					final DAO dao = new DAO("PNPE.database", true);
					registerTextsToDatabase(dao, texts);
					registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.CONTROL,
							frequenciesForControlDependence);
					registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.DATA,
							frequenciesForDataDependence);
					registerFrequenciesToDatabase(dao, DEPENDENCE_TYPE.EXECUTION,
							frequenciesForExecutionDependence);
					dao.close();
					
					while (true) {

						if (null == text || text.isDisposed()) {
							break;
						}

						text.getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								text.append("aaa");
								text.append(System
										.getProperty("line.separator"));
							}
						});

						try {
							Thread.sleep(1000);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					System.out.println("bbb");
				}
			}
		}.start();
	}

	// private void hookContextMenu() {
	// MenuManager menuMgr = new MenuManager("#PopupMenu");
	// menuMgr.setRemoveAllWhenShown(true);
	// menuMgr.addMenuListener(new IMenuListener() {
	// public void menuAboutToShow(IMenuManager manager) {
	// PNPEView.this.fillContextMenu(manager);
	// }
	// });
	// Menu menu = menuMgr.createContextMenu(viewer.getControl());
	// viewer.getControl().setMenu(menu);
	// getSite().registerContextMenu(menuMgr, viewer);
	// }
	//
	// private void contributeToActionBars() {
	// IActionBars bars = getViewSite().getActionBars();
	// fillLocalPullDown(bars.getMenuManager());
	// fillLocalToolBar(bars.getToolBarManager());
	// }
	//
	// private void fillLocalPullDown(IMenuManager manager) {
	// manager.add(action1);
	// manager.add(new Separator());
	// manager.add(action2);
	// }
	//
	// private void fillContextMenu(IMenuManager manager) {
	// manager.add(action1);
	// manager.add(action2);
	// // Other plug-ins can contribute there actions here
	// manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	// }
	//
	// private void fillLocalToolBar(IToolBarManager manager) {
	// manager.add(action1);
	// manager.add(action2);
	// }
	//
	// private void makeActions() {
	// action1 = new Action() {
	// public void run() {
	// showMessage("Action 1 executed");
	// }
	// };
	// action1.setText("Action 1");
	// action1.setToolTipText("Action 1 tooltip");
	// action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
	// .getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
	//
	// action2 = new Action() {
	// public void run() {
	// showMessage("Action 2 executed");
	// }
	// };
	// action2.setText("Action 2");
	// action2.setToolTipText("Action 2 tooltip");
	// action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
	// .getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
	// doubleClickAction = new Action() {
	// public void run() {
	// ISelection selection = viewer.getSelection();
	// Object obj = ((IStructuredSelection) selection)
	// .getFirstElement();
	// showMessage("Double-click detected on " + obj.toString());
	// }
	// };
	// }
	//
	// private void hookDoubleClickAction() {
	// viewer.addDoubleClickListener(new IDoubleClickListener() {
	// public void doubleClick(DoubleClickEvent event) {
	// doubleClickAction.run();
	// }
	// });
	// }
	//
	// private void showMessage(String message) {
	// MessageDialog.openInformation(viewer.getControl().getShell(),
	// "PNPE View", message);
	// }

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		text.setFocus();
	}

	private static String getNormalizedText(final PDGNode<?> node) {
		final NormalizedText fromNodeNormalizedText1 = new NormalizedText(
				node.core);
		final String fromNodeNormalizedText2 = NormalizedText
				.normalize(fromNodeNormalizedText1.getText());
		return fromNodeNormalizedText2;
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
			final ConcurrentMap<Integer, AtomicInteger> fromNodeAllFrequencies,
			final ConcurrentMap<Integer, ConcurrentMap<Integer, AtomicInteger>> toNodeAllFrequencies,
			final ConcurrentMap<Integer, String> texts,
			final ConcurrentMap<Integer, List<Frequency>> allFrequencies) {

		for (final Entry<Integer, ConcurrentMap<Integer, AtomicInteger>> entry : toNodeAllFrequencies
				.entrySet()) {
			final int fromNodeHash = entry.getKey();
			final int totalTime = fromNodeAllFrequencies.get(fromNodeHash)
					.get();
			final List<Frequency> frequencies = new ArrayList<Frequency>();
			final ConcurrentMap<Integer, AtomicInteger> toNodeFrequencies = entry
					.getValue();
			for (final Entry<Integer, AtomicInteger> entry2 : toNodeFrequencies
					.entrySet()) {
				final int toNodeHash = entry2.getKey();
				final int time = entry2.getValue().get();
				final String normalizedText = texts.get(toNodeHash);
				final Frequency frequency = new Frequency((float) time
						/ (float) totalTime, time, toNodeHash, normalizedText);
				frequencies.add(frequency);
			}
			Collections.sort(frequencies, new Comparator<Frequency>() {
				@Override
				public int compare(final Frequency f1, final Frequency f2) {
					if (f1.probablity > f2.probablity) {
						return -1;
					} else if (f1.probablity < f2.probablity) {
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

	private static void registerFrequenciesToDatabase(final DAO dao,
			DEPENDENCE_TYPE type,
			final ConcurrentMap<Integer, List<Frequency>> allFrequencies) {

		for (final Entry<Integer, List<Frequency>> entry : allFrequencies
				.entrySet()) {

			final int fromhash = entry.getKey();
			for (final Frequency frequency : entry.getValue()) {
				dao.addToFrequencies(type, fromhash, frequency);
			}
		}
	}

}