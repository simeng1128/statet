/*******************************************************************************
 * Copyright (c) 2005-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.base.ui.sourceeditors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.link.ILinkedModeListener;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.IProjectionListener;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.TextEditorAction;
import org.eclipse.ui.texteditor.templates.ITemplatesPage;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import de.walware.ecommons.ltk.IModelElement;
import de.walware.ecommons.ltk.ISourceStructElement;
import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ltk.ISourceUnitModelInfo;
import de.walware.ecommons.ltk.ast.IAstNode;
import de.walware.ecommons.ltk.ui.IModelElementInputProvider;
import de.walware.ecommons.ltk.ui.ISelectionWithElementInfoListener;
import de.walware.ecommons.ltk.ui.LTKInputData;
import de.walware.ecommons.ltk.ui.PostSelectionCancelExtension;
import de.walware.ecommons.ltk.ui.PostSelectionWithElementInfoController;
import de.walware.ecommons.ltk.ui.PostSelectionWithElementInfoController.IgnoreActivation;
import de.walware.ecommons.preferences.Preference;
import de.walware.ecommons.preferences.PreferencesUtil;
import de.walware.ecommons.preferences.SettingsChangeNotifier;
import de.walware.ecommons.text.PartitioningConfiguration;
import de.walware.ecommons.ui.text.PairMatcher;
import de.walware.ecommons.ui.text.sourceediting.GotoMatchingBracketHandler;
import de.walware.ecommons.ui.text.sourceediting.ISourceEditor;
import de.walware.ecommons.ui.text.sourceediting.ISourceEditorAddon;
import de.walware.ecommons.ui.text.sourceediting.ITextEditToolSynchronizer;
import de.walware.ecommons.ui.text.sourceediting.SourceEditorViewerConfigurator;
import de.walware.ecommons.ui.text.sourceediting.StructureSelectHandler;
import de.walware.ecommons.ui.text.sourceediting.StructureSelectionHistory;
import de.walware.ecommons.ui.text.sourceediting.StructureSelectionHistoryBackHandler;
import de.walware.ecommons.ui.util.ISettingsChangedHandler;
import de.walware.ecommons.ui.util.UIAccess;

import de.walware.statet.base.core.StatetExtNature;
import de.walware.statet.base.internal.ui.StatetMessages;
import de.walware.statet.base.internal.ui.StatetUIPlugin;
import de.walware.statet.base.ui.IStatetUICommandIds;
import de.walware.statet.base.ui.IStatetUIMenuIds;


public abstract class StatextEditor1<ProjectT extends StatetExtNature> extends TextEditor
		implements ISourceEditor, 
			SettingsChangeNotifier.ChangeListener, IPreferenceChangeListener,
			IShowInSource, IShowInTargetList {
	
	
	public static final String ACTION_ID_TOGGLE_COMMENT = "de.walware.statet.ui.actions.ToggleComment"; //$NON-NLS-1$
	
	
/*- Static utility methods --------------------------------------------------*/
	
	/**
	 * Returns the lock object for the given annotation model.
	 * 
	 * @param annotationModel the annotation model
	 * @return the annotation model's lock object
	 */
	protected static Object getLockObject(final IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			final Object lock = ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}
	
	/**
	 * Creates a region describing the text block (something that starts at
	 * the beginning of a line) completely containing the current selection.
	 * 
	 * @param selection The selection to use
	 * @param document The document
	 * @return the region describing the text block comprising the given selection
	 */
	protected static IRegion getTextBlockFromSelection(final ITextSelection selection, final IDocument document) {
		try {
			final int firstLine = document.getLineOfOffset(selection.getOffset());
			int lastLine = document.getLineOfOffset(selection.getOffset()+selection.getLength());
			final int offset = document.getLineOffset(firstLine);
			int lastLineOffset = document.getLineOffset(lastLine);
			if (firstLine != lastLine && lastLineOffset == selection.getOffset()+selection.getLength()) {
				lastLine--;
				lastLineOffset = document.getLineOffset(lastLine);
			}
			return new Region(offset, lastLineOffset+document.getLineLength(lastLine)-offset);
		}
		catch (final BadLocationException x) {
			StatetUIPlugin.logUnexpectedError(x); // should not happen
		}
		return null;
	}
	
	/**
	 * Returns the index of the first line whose start offset is in the given text range.
	 * 
	 * @param region the text range in characters where to find the line
	 * @param document The document
	 * @return the first line whose start index is in the given range, -1 if there is no such line
	 */
	protected static int getFirstCompleteLineOfRegion(final IRegion region, final IDocument document) {
		try {
			final int startLine = document.getLineOfOffset(region.getOffset());
			
			int offset = document.getLineOffset(startLine);
			if (offset >= region.getOffset()) {
				return startLine;
			}
			offset = document.getLineOffset(startLine + 1);
			return (offset > region.getOffset() + region.getLength() ? -1 : startLine + 1);
		} catch (final BadLocationException x) {
			StatetUIPlugin.logUnexpectedError(x);	// should not happen
		}
		return -1;
	}
	
	
/*- Inner classes -----------------------------------------------------------*/
	
	protected class PostSelectionEditorCancel extends PostSelectionCancelExtension {
		
		public PostSelectionEditorCancel() {
		}
		
		@Override
		public void init() {
			final ISourceViewer viewer = getSourceViewer();
			if (viewer != null) {
				viewer.addTextInputListener(this);
				viewer.getDocument().addDocumentListener(this);
			}
		}
		
		@Override
		public void dispose() {
			final ISourceViewer viewer = getSourceViewer();
			if (viewer != null) {
				viewer.removeTextInputListener(this);
				final IDocument document = viewer.getDocument();
				if (document != null) {
					document.removeDocumentListener(this);
				}
			}
		}
	}
	
	protected class ToggleCommentAction extends TextEditorAction {
		
		/** The text operation target */
		private ITextOperationTarget fOperationTarget;
		/** The document partitioning */
		private String fDocumentPartitioning;
		/** The comment prefixes */
		private Map<String, String[]> fPrefixesMap;
		
		public ToggleCommentAction() {
			super(EditorMessages.getCompatibilityBundle(), "ToggleCommentAction_", StatextEditor1.this); //$NON-NLS-1$
			setActionDefinitionId(IStatetUICommandIds.TOGGLE_COMMENT);
			
			configure();
		}
		
		@Override
		public void run() {
			final ISourceViewer sourceViewer = getSourceViewer();
			
			if (!validateEditorInputState())
				return;
			
			final int operationCode = (isSelectionCommented(getSelectionProvider().getSelection())) ?
				ITextOperationTarget.STRIP_PREFIX : ITextOperationTarget.PREFIX;
			
			final Shell shell = getSite().getShell();
			if (!fOperationTarget.canDoOperation(operationCode)) {
				setStatusLineErrorMessage(EditorMessages.ToggleCommentAction_error);
				sourceViewer.getTextWidget().getDisplay().beep();
				return;
			}
			
			Display display = null;
			if (shell != null && !shell.isDisposed())
				display = shell.getDisplay();
			
			BusyIndicator.showWhile(display, new Runnable() {
				public void run() {
					fOperationTarget.doOperation(operationCode);
				}
			});
		}
		
		/**
		 * Implementation of the <code>IUpdate</code> prototype method discovers
		 * the operation through the current editor's
		 * <code>ITextOperationTarget</code> adapter, and sets the enabled state
		 * accordingly.
		 */
		@Override
		public void update() {
			super.update();
			
			if (!canModifyEditor()) {
				setEnabled(false);
				return;
			}
			
			final ITextEditor editor = getTextEditor();
			if (fOperationTarget == null && editor != null)
				fOperationTarget = (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);
			
			setEnabled(fOperationTarget != null
					&& fOperationTarget.canDoOperation(ITextOperationTarget.PREFIX)
					&& fOperationTarget.canDoOperation(ITextOperationTarget.STRIP_PREFIX) );
		}
		
		private void configure() {
			final SourceViewerConfiguration configuration = getSourceViewerConfiguration();
			final ISourceViewer sourceViewer = getSourceViewer();
			
			final String[] types = configuration.getConfiguredContentTypes(sourceViewer);
			fPrefixesMap = new HashMap<String, String[]>(types.length);
			for (int i= 0; i < types.length; i++) {
				final String type = types[i];
				String[] prefixes = configuration.getDefaultPrefixes(sourceViewer, type);
				if (prefixes != null && prefixes.length > 0) {
					int emptyPrefixes = 0;
					for (int j= 0; j < prefixes.length; j++)
						if (prefixes[j].length() == 0)
							emptyPrefixes++;
					
					if (emptyPrefixes > 0) {
						final String[] nonemptyPrefixes = new String[prefixes.length - emptyPrefixes];
						for (int j = 0, k = 0; j < prefixes.length; j++) {
							final String prefix = prefixes[j];
							if (prefix.length() != 0) {
								nonemptyPrefixes[k]= prefix;
								k++;
							}
						}
						prefixes = nonemptyPrefixes;
					}
					fPrefixesMap.put(type, prefixes);
				}
			}
			
			fDocumentPartitioning = configuration.getConfiguredDocumentPartitioning(sourceViewer);
		}
		
		/**
		 * Is the given selection single-line commented?
		 * 
		 * @param selection Selection to check
		 * @return <code>true</code> iff all selected lines are commented
		 */
		private boolean isSelectionCommented(final ISelection selection) {
			if (!(selection instanceof ITextSelection))
				return false;
			
			final ITextSelection textSelection = (ITextSelection) selection;
			if (textSelection.getStartLine() < 0 || textSelection.getEndLine() < 0)
				return false;
			
			final IDocument document = getDocumentProvider().getDocument(getEditorInput());
			try {
				final IRegion block = getTextBlockFromSelection(textSelection, document);
				final ITypedRegion[] regions = TextUtilities.computePartitioning(document, fDocumentPartitioning, block.getOffset(), block.getLength(), false);
				
				final int[] lines = new int[regions.length * 2]; // [startline, endline, startline, endline, ...]
				for (int i = 0, j = 0; i < regions.length; i++, j+= 2) {
					// start line of region
					lines[j] = getFirstCompleteLineOfRegion(regions[i], document);
					// end line of region
					final int length = regions[i].getLength();
					int offset = regions[i].getOffset() + length;
					if (length > 0) {
						offset--;
					}
					lines[j+1]= (lines[j] == -1) ? -1 : document.getLineOfOffset(offset);
				}
				
				// Perform the check
				for (int i = 0, j = 0; i < regions.length; i++, j+=2) {
					final String[] prefixes = fPrefixesMap.get(regions[i].getType());
					if (prefixes != null && prefixes.length > 0 && lines[j] >= 0 && lines[j + 1] >= 0)
						if (!isBlockCommented(lines[j], lines[j + 1], prefixes, document))
							return false;
				}
				return true;
				
			} catch (final BadLocationException x) {
				StatetUIPlugin.logUnexpectedError(x);		// should not happen
			}
			return false;
		}
		
		/**
		 * Determines whether each line is prefixed by one of the prefixes.
		 * 
		 * @param startLine Start line in document
		 * @param endLine End line in document
		 * @param prefixes Possible comment prefixes
		 * @param document The document
		 * @return <code>true</code> iff each line from <code>startLine</code>
		 *     to and including <code>endLine</code> is prepended by one
		 *     of the <code>prefixes</code>, ignoring whitespace at the
		 *     begin of line
		 */
		private boolean isBlockCommented(final int startLine, final int endLine, final String[] prefixes, final IDocument document) {
			try {
				// check for occurrences of prefixes in the given lines
				for (int i = startLine; i <= endLine; i++) {
					
					final IRegion line = document.getLineInformation(i);
					final String text = document.get(line.getOffset(), line.getLength());
					
					final int[] found = TextUtilities.indexOf(prefixes, text, 0);
					
					if (found[0] == -1)
						// found a line which is not commented
						return false;
					
					String s = document.get(line.getOffset(), found[0]);
					s = s.trim();
					if (s.length() != 0)
						// found a line which is not commented
						return false;
				}
				return true;
				
			} catch (final BadLocationException x) {
				StatetUIPlugin.logUnexpectedError(x);		// should not happen
			}
			return false;
		}
		
	}
	
	private class EffectSynchonizer implements ITextEditToolSynchronizer, ILinkedModeListener {
		
		private EffectSynchonizer() {
		}
		
		public void install(final LinkedModeModel model) {
			fEffectSynchonizerCounter++;
			if (fMarkOccurrencesProvider != null) {
				fMarkOccurrencesProvider.uninstall();
			}
			model.addLinkingListener(this);
		}
		
		public void left(final LinkedModeModel model, final int flags) {
			fEffectSynchonizerCounter--;
			updateMarkOccurrencesEnablement();
		}
		
		public void resume(final LinkedModeModel model, final int flags) {
		}
		
		public void suspend(final LinkedModeModel model) {
		}
		
	}
	
	
/*- Fields -----------------------------------------------------------------*/
	
	private SourceEditorViewerConfigurator fConfigurator;
	private boolean fLazySetup;
	private String fProjectNatureId;
	private ProjectT fProject;
	private IModelElementInputProvider fModelProvider;
	private PostSelectionWithElementInfoController fModelPostSelection;
	protected volatile Point fCurrentSelection;
	
	/** The outline page of this editor */
	private StatextOutlinePage1 fOutlinePage;
	
	/** The templates page of this editor */
	private ITemplatesPage fTemplatesPage;
	
	private StructureSelectionHistory fSelectionHistory;
	private Preference<Boolean> fFoldingEnablement;
	private ProjectionSupport fFoldingSupport;
	private ISourceEditorAddon fFoldingProvider;
	private FoldingActionGroup fFoldingActionGroup;
	private Preference<Boolean> fMarkOccurrencesEnablement;
	private ISourceEditorAddon fMarkOccurrencesProvider;
	
	private EffectSynchonizer fEffectSynchronizer;
	private int fEffectSynchonizerCounter;
	
	private List<IUpdate> fUpdateables = new ArrayList<IUpdate>();
	
	
/*- Contructors ------------------------------------------------------------*/
	
	public StatextEditor1() {
		super();
	}
	
	
/*- Methods ----------------------------------------------------------------*/
	
	@Override
	protected void initializeEditor() {
		fConfigurator = createConfiguration();
		super.initializeEditor();
		setCompatibilityMode(false);
		setPreferenceStore(fConfigurator.getSourceViewerConfiguration().getPreferences());
		setSourceViewerConfiguration(fConfigurator.getSourceViewerConfiguration());
		
		PreferencesUtil.getSettingsChangeNotifier().addChangeListener(this);
	}
	
	protected abstract SourceEditorViewerConfigurator createConfiguration();
	
	
	protected void enableStructuralFeatures(final IModelElementInputProvider provider,
			final Preference<Boolean> codeFoldingEnablement,
			final Preference<Boolean> markOccurrencesEnablement) {
		fModelProvider = provider;
		fFoldingEnablement = codeFoldingEnablement;
		fMarkOccurrencesEnablement = markOccurrencesEnablement;
	}
	
	protected void configureStatetProjectNatureId(final String id) {
		fProjectNatureId = id;
	}
	
	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { "de.walware.statet.base.contexts.TextEditor" }); //$NON-NLS-1$
	}
	
	@Override
	protected String[] collectContextMenuPreferencePages() {
		final List<String> list = new ArrayList<String>();
		collectContextMenuPreferencePages(list);
		list.addAll(Arrays.asList(super.collectContextMenuPreferencePages()));
		return list.toArray(new String[list.size()]);
	}
	
	protected void collectContextMenuPreferencePages(final List<String> pageIds) {
	}
	
	
	@Override
	protected void doSetInput(final IEditorInput input) throws CoreException {
		final ProjectT prevProject = fProject;
		fProject = (input != null) ? getProject(input) : null;
		
		// project has changed
		final ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer != null) {
			fConfigurator.unconfigureTarget();
		}
		
		super.doSetInput(input);
		
		if (input != null) {
			setupConfiguration(prevProject, fProject, input);
			if (sourceViewer != null) {
				setupConfiguration(prevProject, fProject, input, sourceViewer);
				fConfigurator.configureTarget();
			}
			else {
				fLazySetup = true;
			}
			if (fOutlinePage != null) {
				updateOutlinePageInput(fOutlinePage);
			}
		}
	}
	
	/**
	 * Subclasses should setup the SourceViewerConfiguration.
	 */
	protected void setupConfiguration(final ProjectT prevProject, final ProjectT newProject, final IEditorInput newInput) {
	}
	
	/**
	 * Subclasses should setup the SourceViewerConfiguration.
	 */
	protected void setupConfiguration(final ProjectT prevProject, final ProjectT newProject, final IEditorInput newInput,
			final ISourceViewer sourceViewer) {
	}
	
	/**
	 * Subclasses should implement this method, if it want to use project features.
	 * 
	 * @param input a editor input.
	 * @return the project, the input is associated to, or <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	protected ProjectT getProject(final IEditorInput input) {
		if (fProjectNatureId != null) {
			if (input != null && input instanceof IFileEditorInput) {
				final IProject project = ((IFileEditorInput)input).getFile().getProject();
				try {
					if (project != null && project.hasNature(fProjectNatureId)) {
						return (ProjectT) project.getNature(fProjectNatureId);
					}
				} catch (final CoreException e) {
					// pech gehabt
				}
			}
		}
		return null;
	}
	
	/**
	 * @return the project of current input, or <code>null</code>, if none/not supported.
	 */
	protected final ProjectT getProject() {
		return fProject;
	}
	
	public ISourceUnit getSourceUnit() {
		return null;
	}
	
	
	public SourceViewer getViewer() {
		return (SourceViewer) super.getSourceViewer();
	}
	
	public PartitioningConfiguration getPartitioning() {
		return fConfigurator.getPartitioning();
	}
	
	public IWorkbenchPart getWorkbenchPart() {
		return this;
	}
	
	public IServiceLocator getServiceLocator() {
		return getSite();
	}
	
	public boolean isEditable(final boolean validate) {
		if (validate) {
			return StatextEditor1.this.validateEditorInputState();
		}
		return StatextEditor1.this.isEditorInputModifiable();
	}
	
	public IModelElementInputProvider getModelInputProvider() {
		return fModelProvider;
	}
	
	public void addPostSelectionWithElementInfoListener(final ISelectionWithElementInfoListener listener) {
		if (fModelPostSelection != null) {
			fModelPostSelection.addListener(listener);
		}
	}
	
	public void removePostSelectionWithElementInfoListener(final ISelectionWithElementInfoListener listener) {
		if (fModelPostSelection != null) {
			fModelPostSelection.removeListener(listener);
		}
	}
	
	
	@Override
	public void createPartControl(final Composite parent) {
		super.createPartControl(parent);
		
		if (fModelProvider != null) {
			fModelPostSelection = new PostSelectionWithElementInfoController(fModelProvider,
					(IPostSelectionProvider) getSelectionProvider(), new PostSelectionEditorCancel());
			fModelPostSelection.addListener(new ISelectionWithElementInfoListener() {
				public void inputChanged() {
				}
				public void stateChanged(final LTKInputData state) {
					final IRegion toHighlight = getRangeToHighlight(state);
					if (toHighlight != null) {
						setHighlightRange(toHighlight.getOffset(), toHighlight.getLength(), false);
					}
					else {
						resetHighlightRange();
					}
				}
			});
		}
		if (fFoldingEnablement != null) {
			final ProjectionViewer viewer = (ProjectionViewer) getSourceViewer();
			
			fFoldingSupport = new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());
			fFoldingSupport.install();
			viewer.addProjectionListener(new IProjectionListener() {
				public void projectionEnabled() {
					installFoldingProvider();
				}
				public void projectionDisabled() {
					uninstallFoldingProvider();
				}
			});
			PreferencesUtil.getInstancePrefs().addPreferenceNodeListener(
					fFoldingEnablement.getQualifier(), this);
			updateFoldingEnablement();
		}
		if (fMarkOccurrencesEnablement != null) {
			PreferencesUtil.getInstancePrefs().addPreferenceNodeListener(
					fMarkOccurrencesEnablement.getQualifier(), this);
			updateMarkOccurrencesEnablement();
		}
		
		if (fLazySetup) {
			fLazySetup = false;
			setupConfiguration(null, fProject, getEditorInput(), getSourceViewer());
			fConfigurator.setTarget(this);
		}
	}
	
	@Override
	protected ISourceViewer createSourceViewer(final Composite parent, final IVerticalRuler ruler, final int styles) {
		fAnnotationAccess = getAnnotationAccess();
		fOverviewRuler = createOverviewRuler(getSharedColors());
		
		final ISourceViewer viewer = (fFoldingEnablement != null) ?
				new ProjectionViewer(parent, ruler, getOverviewRuler(), isOverviewRulerVisible(), styles) :
					new SourceViewer(parent, ruler, getOverviewRuler(), isOverviewRulerVisible(), styles);
		// ensure decoration support has been created and configured.
		getSourceViewerDecorationSupport(viewer);
		
		return viewer;
	}
	
	protected IRegion getRangeToHighlight(final LTKInputData state) {
		final ISourceUnitModelInfo info = state.getInputInfo();
		if (info == null) {
			return null;
		}
		ISourceStructElement element = state.getModelSelection();
		TRY_MODEL: while (element != null) {
			switch (element.getElementType() & IModelElement.MASK_C1) {
			case IModelElement.C1_CLASS:
			case IModelElement.C1_METHOD:
				return element.getSourceRange();
			case IModelElement.C1_SOURCE:
				if ((element.getElementType() & IModelElement.MASK_C2) == IModelElement.C2_SOURCE_CHUNK) {
					return element.getSourceRange();
				}
				break TRY_MODEL;
			default:
				element = element.getSourceParent();
				continue TRY_MODEL;
			}
		}
		final IAstNode root = info.getAst().root;
		TRY_AST: if (root != null) {
			final ITextSelection selection = (ITextSelection) state.getSelection();
			final int n = root.getChildCount();
			for (int i = 0; i < n; i++) {
				final IAstNode child = root.getChild(i);
				if (selection.getOffset() >= child.getOffset()) {
					if (selection.getOffset()+selection.getLength() <= child.getStopOffset()) {
						return child;
					}
				}
				else {
					break TRY_AST;
				}
			}
		}
		return null;
	}
	
	
	protected ISourceEditorAddon createCodeFoldingProvider() {
		return null;
	}
	
	private void installFoldingProvider() {
		uninstallFoldingProvider();
		fFoldingProvider = createCodeFoldingProvider();
		if (fFoldingProvider != null) {
			fFoldingProvider.install(this);
		}
	}
	
	private void uninstallFoldingProvider() {
		if (fFoldingProvider != null) {
			fFoldingProvider.uninstall();
			fFoldingProvider = null;
		}
	}
	
	private void updateFoldingEnablement() {
		if (fFoldingEnablement != null) {
			UIAccess.getDisplay().asyncExec(new Runnable() {
				public void run() {
					final Boolean enable = PreferencesUtil.getInstancePrefs().getPreferenceValue(fFoldingEnablement);
					final ProjectionViewer viewer = (ProjectionViewer) getSourceViewer();
					if (enable != null && UIAccess.isOkToUse(viewer)) {
						if (enable != viewer.isProjectionMode()) {
							viewer.doOperation(ProjectionViewer.TOGGLE);
						}
					}
				}
			});
		}
	}
	
	
	protected ISourceEditorAddon createMarkOccurrencesProvider() {
		return null;
	}
	
	private void uninstallMarkOccurrencesProvider() {
		if (fMarkOccurrencesProvider != null) {
			fMarkOccurrencesProvider.uninstall();
			fMarkOccurrencesProvider = null;
		}
	}
	
	private void updateMarkOccurrencesEnablement() {
		if (fMarkOccurrencesEnablement != null) {
			UIAccess.getDisplay().asyncExec(new Runnable() {
				public void run() {
					final Boolean enable = PreferencesUtil.getInstancePrefs().getPreferenceValue(fMarkOccurrencesEnablement);
					if (enable) {
						if (fMarkOccurrencesProvider == null) {
							fMarkOccurrencesProvider = createMarkOccurrencesProvider();
						}
						if (fMarkOccurrencesProvider != null && fEffectSynchonizerCounter == 0) {
							fMarkOccurrencesProvider.install(StatextEditor1.this);
						}
					}
					else {
						uninstallMarkOccurrencesProvider();
					}
				}
			});
		}
	}
	
	
	@Override
	protected void configureSourceViewerDecorationSupport(final SourceViewerDecorationSupport support) {
		super.configureSourceViewerDecorationSupport(support);
		fConfigurator.configureSourceViewerDecorationSupport(support);
	}
	
	@Override
	protected void createActions() {
		super.createActions();
		final IHandlerService handlerService = (IHandlerService) getServiceLocator().getService(IHandlerService.class);
		IAction action;
		
		final PairMatcher matcher = fConfigurator.getPairMatcher();
		if (matcher != null) {
			handlerService.activateHandler(IStatetUICommandIds.GOTO_MATCHING_BRACKET,
					new GotoMatchingBracketHandler(matcher, this));
		}
		
		action = createToggleCommentAction();
		if (action != null) {
			setAction(ACTION_ID_TOGGLE_COMMENT, action);
			markAsStateDependentAction(ACTION_ID_TOGGLE_COMMENT, true);
		}
		
		action = createCorrectIndentAction();
		if (action != null) {
			setAction(action.getId(), action);
			markAsContentDependentAction(action.getId(), true);
		}
		
		if (fFoldingEnablement != null) {
			fFoldingActionGroup = createFoldingActionGroup();
		}
		
		if (fModelProvider != null) {
			fSelectionHistory = new StructureSelectionHistory(this);
			handlerService.activateHandler(IStatetUICommandIds.SELECT_ENCLOSING,
					new StructureSelectHandler.Enclosing(this, fSelectionHistory));
			handlerService.activateHandler(IStatetUICommandIds.SELECT_PREVIOUS,
					new StructureSelectHandler.Previous(this, fSelectionHistory));
			handlerService.activateHandler(IStatetUICommandIds.SELECT_NEXT,
					new StructureSelectHandler.Next(this, fSelectionHistory));
			final StructureSelectionHistoryBackHandler backHandler = new StructureSelectionHistoryBackHandler(this, fSelectionHistory);
			handlerService.activateHandler(IStatetUICommandIds.SELECT_LAST, backHandler);
			fSelectionHistory.addUpdateListener(backHandler);
		}
		//WorkbenchHelp.setHelp(action, IJavaHelpContextIds.TOGGLE_COMMENT_ACTION);
	}
	
	protected FoldingActionGroup createFoldingActionGroup() {
		return new FoldingActionGroup(this, (ProjectionViewer) getSourceViewer());
	}
	
	protected IAction createToggleCommentAction() {
		return new ToggleCommentAction();
	}
	
	protected IAction createCorrectIndentAction() {
		return null;
	}
	
	protected void markAsContentDependentHandler(final IHandler handler, final boolean update) {
		if (update) {
			if (handler instanceof IUpdate) {
				fUpdateables.add((IUpdate) handler);
			}
		}
		else {
			fUpdateables.remove(handler);
		}
	}
	
	@Override
	protected void updateContentDependentActions() {
		super.updateContentDependentActions();
		for (final IUpdate o : fUpdateables) {
			o.update();
		}
	}
	
	@Override
	protected void editorContextMenuAboutToShow(final IMenuManager m) {
		super.editorContextMenuAboutToShow(m);
		
		m.insertBefore(IStatetUIMenuIds.GROUP_ADDITIONS_ID, new Separator(IStatetUIMenuIds.GROUP_RUN_STAT_ID));
		final IContributionItem additions = m.find(IStatetUIMenuIds.GROUP_ADDITIONS_ID);
		if (additions != null) {
			additions.setVisible(false);
		}
	}
	
	@Override
	protected void rulerContextMenuAboutToShow(final IMenuManager menu) {
		super.rulerContextMenuAboutToShow(menu);
		if (fFoldingActionGroup != null) {
			final IMenuManager foldingMenu = new MenuManager(StatetMessages.CodeFolding_label, "projection"); //$NON-NLS-1$
			menu.appendToGroup(ITextEditorActionConstants.GROUP_RULERS, foldingMenu);
			fFoldingActionGroup.fillMenu(foldingMenu);
		}
	}
	
	
	public ITextEditToolSynchronizer getTextEditToolSynchronizer() {
		if (fEffectSynchronizer == null) {
			fEffectSynchronizer = new EffectSynchonizer();
		}
		return fEffectSynchronizer;
	}
	
	@Override
	public Object getAdapter(final Class required) {
		if (ISourceEditor.class.equals(required)) {
			return this;
		}
		if (IContentOutlinePage.class.equals(required)) {
			if (fOutlinePage == null) {
				fOutlinePage = createOutlinePage();
				if (fOutlinePage != null) {
					updateOutlinePageInput(fOutlinePage);
				}
			}
			return fOutlinePage;
		}
		if (ITemplatesPage.class.equals(required)) {
			if (fTemplatesPage == null) {
				fTemplatesPage = createTemplatesPage();
			}
			return fTemplatesPage;
		}
		if (ISourceViewer.class.equals(required)) {
			return getSourceViewer();
		}
		if (fFoldingSupport != null) {
			final Object adapter = fFoldingSupport.getAdapter(getSourceViewer(), required);
			if (adapter != null)
				return adapter;
		}
		
		return super.getAdapter(required);
	}
	
	
	public void settingsChanged(final Set<String> groupIds) {
		final Map<String, Object> options = new HashMap<String, Object>();
		UIAccess.getDisplay().syncExec(new Runnable() {
			public void run() {
				handleSettingsChanged(groupIds, options);
			}
		});
	}
	
	/**
	 * @see ISettingsChangedHandler#handleSettingsChanged(Set, Map)
	 */
	protected void handleSettingsChanged(final Set<String> groupIds, final Map<String, Object> options) {
		if (fConfigurator != null) {
			fConfigurator.handleSettingsChanged(groupIds, options);
		}
	}
	
	public void preferenceChange(final PreferenceChangeEvent event) {
		if (fFoldingEnablement != null && event.getKey().equals(fFoldingEnablement.getKey())) {
			updateFoldingEnablement();
		}
		if (fMarkOccurrencesEnablement != null && event.getKey().equals(fMarkOccurrencesEnablement.getKey())) {
			updateMarkOccurrencesEnablement();
		}
	}
	
	@Override
	protected void handleCursorPositionChanged() {
		fCurrentSelection = getSourceViewer().getSelectedRange();
		super.handleCursorPositionChanged();
	}
	
	
	protected StatextOutlinePage1 createOutlinePage() {
		return null;
	}
	
	protected void updateOutlinePageInput(final StatextOutlinePage1 page) {
	}
	
	void handleOutlinePageClosed() {
		if (fOutlinePage != null) {
			fOutlinePage = null;
			resetHighlightRange();
		}
	}
	
	protected ITemplatesPage createTemplatesPage() {
		return null;
	}
	
	
	@Override
	// inject annotation painter workaround
	protected SourceViewerDecorationSupport getSourceViewerDecorationSupport(final ISourceViewer viewer) {
		if (fSourceViewerDecorationSupport == null) {
			fSourceViewerDecorationSupport= new de.walware.epatches.ui.SourceViewerDecorationSupport(viewer, getOverviewRuler(), getAnnotationAccess(), getSharedColors());
			configureSourceViewerDecorationSupport(fSourceViewerDecorationSupport);
		}
		return fSourceViewerDecorationSupport;
	}
	
	@Override
	public void selectAndReveal(final int start, final int length) {
		if (fModelPostSelection != null) {
			fModelPostSelection.setUpdateOnSelection(true);
			try {
				super.selectAndReveal(start, length);
			}
			finally {
				fModelPostSelection.setUpdateOnSelection(false);
			}
		}
		else {
			super.selectAndReveal(start, length);
		}
	}
	
	public void setSelection(final ISelection selection, final ISelectionWithElementInfoListener listener) {
		if (fModelPostSelection != null && listener != null) {
			final IgnoreActivation activation = fModelPostSelection.ignoreNext(listener);
			doSetSelection(selection);
			activation.deleteNext();
		}
		else {
			doSetSelection(selection);
		}
	}
	
	@Override
	protected void doSetSelection(final ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection structured = (IStructuredSelection) selection;
			if (!structured.isEmpty()) {
				final Object first = structured.getFirstElement();
				IRegion region = null;
				if (first instanceof ISourceStructElement) {
					region = ((ISourceStructElement) first).getNameSourceRange();
					if (region == null) {
						region = ((ISourceStructElement) first).getSourceRange();
					}
				}
				if (region == null && first instanceof IRegion) {
					region = (IRegion) first;
				}
				if (region != null) {
					selectAndReveal(region.getOffset(), region.getLength());
					return;
				}
			}
		}
		super.doSetSelection(selection);
	}
	
	
	@Override
	public void dispose() {
		PreferencesUtil.getSettingsChangeNotifier().removeChangeListener(this);
		if (fModelPostSelection != null) {
			fModelPostSelection.dispose();
		}
		if (fFoldingEnablement != null) {
			PreferencesUtil.getInstancePrefs().removePreferenceNodeListener(
					fFoldingEnablement.getQualifier(), this);
			uninstallFoldingProvider();
		}
		if (fMarkOccurrencesEnablement != null) {
			PreferencesUtil.getInstancePrefs().removePreferenceNodeListener(
					fMarkOccurrencesEnablement.getQualifier(), this);
			uninstallMarkOccurrencesProvider();
		}
		
		super.dispose();
		fModelPostSelection = null;
	}
	
	public ShowInContext getShowInContext() {
		final Point selectionPoint = fCurrentSelection;
		final ISourceViewer sourceViewer = getSourceViewer();
		final ISourceUnit unit = getSourceUnit();
		ISelection selection = null;
		if (selectionPoint != null && unit != null && sourceViewer != null) {
			selection = new LTKInputData(unit, getSelectionProvider());
		}
		return new ShowInContext(getEditorInput(), selection);
	}
	
	public String[] getShowInTargetIds() {
		return new String[] { ProjectExplorer.VIEW_ID, IPageLayout.ID_OUTLINE };
	}
	
}
