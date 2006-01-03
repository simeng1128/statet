/*******************************************************************************
 * Copyright (c) 2005 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.console;

import java.util.ResourceBundle;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.actions.ClearOutputAction;
import org.eclipse.ui.internal.console.IOConsoleViewer;
import org.eclipse.ui.part.IPageBookViewPage;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.texteditor.FindReplaceAction;
import org.eclipse.ui.texteditor.IWorkbenchActionDefinitionIds;

import de.walware.eclipsecommon.ui.dialogs.Layouter;
import de.walware.statet.nico.ToolRegistry;
import de.walware.statet.ui.SharedMessages;
import de.walware.statet.ui.TextViewerAction;
import de.walware.statet.ui.util.DNDUtil;


/**
 * A page for a <code>NIConsole</code>.
 * <p>
 * The page contains beside the usual output viewer 
 * a separete input field with submit button.
 */
public class NIConsolePage implements IPageBookViewPage, IAdaptable, IPropertyChangeListener, ScrollLockAction.Receiver {

	
	private class FindReplaceUpdater implements IDocumentListener {
		
		private boolean wasEmpty = true;
		
		public void documentAboutToBeChanged(DocumentEvent event) {
		}

		public void documentChanged(DocumentEvent event) {
			
			boolean isEmpty = (event.fDocument.getLength() == 0);
			if (isEmpty != wasEmpty) {
				fMultiActionHandler.updateEnabledState();
				wasEmpty = isEmpty;
			}
		}

	}
	private NIConsole fConsole;
	private IConsoleView fConsoleView;
	
	private IPageSite fSite;
	private Composite fControl;
	private Clipboard fClipboard;
	
	private IOConsoleViewer fOutputViewer;
	private InputGroup fInputGroup;
	
	// Actions
	private MultiActionHandler fMultiActionHandler;
	
	private FindReplaceAction fFindReplaceAction;
	
	private TextViewerAction fOutputCopyAction;
	private SubmitPasteAction fOutputPasteAction;
	private TextViewerAction fOutputSelectAllAction;
	
	private ClearOutputAction fOutputClearAllAction;
	private Action fOutputScrollLockAction;
	
	private TextViewerAction fInputDeleteAction;
	private TextViewerAction fInputCutAction;
	private TextViewerAction fInputCopyAction;
	private TextViewerAction fInputPasteAction;
	private TextViewerAction fInputSelectAllAction;


	/**
	 * Constructs a console page for the given console in the given view.
	 * 
	 * @param console the console
	 * @param view the console view the page is contained in
	 */
	public NIConsolePage(NIConsole console, IConsoleView view) {
		
		fConsole = console;
		fConsoleView = view;
	}
	
	
	public void init(IPageSite site) throws PartInitException {
		
		fSite = site;
		fInputGroup = new InputGroup(fConsole);
	}

	public void createControl(Composite parent) {
		
		fControl = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.verticalSpacing = 3;
		layout.marginWidth = 0;
		layout.marginBottom = 3;
		fControl.setLayout(layout);
		
		fOutputViewer = new IOConsoleViewer(fControl, fConsole);
		fOutputViewer.setReadOnly();
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		fOutputViewer.getControl().setLayoutData(gd);
		
		fInputGroup.createControl(fControl);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		fInputGroup.getComposite().setLayoutData(gd);
		
		fConsole.addPropertyChangeListener(this);

		fClipboard = new Clipboard(fControl.getDisplay());
		createActions();
		hookContextMenu();
		hookDND();
		contributeToActionBars();
		
		new ConsoleActivationNotifier();
	}
	
	private class ConsoleActivationNotifier implements Listener {

		private ToolRegistry fRegistry;
		
		private ConsoleActivationNotifier() {
			
			fRegistry = ToolRegistry.getRegistry(fConsoleView.getViewSite().getPage());
			
			fControl.addListener(SWT.Activate, this);
			fControl.addListener(SWT.Dispose, this);
			if (fControl.isVisible()) {
				fRegistry.consoleActivated(fConsoleView, fConsole);
			}
		}
		
		public void handleEvent(Event event) {
			switch (event.type) {
			
			case SWT.Activate:
				fRegistry.consoleActivated(fConsoleView, fConsole);
				break;
			
			case SWT.Dispose:
				fControl.removeListener(SWT.Activate, this);
				fControl.removeListener(SWT.Dispose, this);
				fRegistry = null;
				break;

			}
		}
	}

	protected void createActions() {
		
		Control outputControl = fOutputViewer.getControl();
		SourceViewer inputViewer = fInputGroup.getSourceViewer();
		Control inputControl = inputViewer.getControl();
		
		fMultiActionHandler = new MultiActionHandler();

		fOutputCopyAction = TextViewerAction.createCopyAction(fOutputViewer);
		fMultiActionHandler.addAction(outputControl, ActionFactory.COPY.getId(), fOutputCopyAction);
	
		fOutputPasteAction = new SubmitPasteAction(this);
		fOutputPasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE);
		fMultiActionHandler.addAction(outputControl, ActionFactory.PASTE.getId(), fOutputPasteAction);
		
		fOutputSelectAllAction = TextViewerAction.createSelectAllAction(fOutputViewer);
		fMultiActionHandler.addAction(outputControl, ActionFactory.SELECT_ALL.getId(), fOutputSelectAllAction);
		
		fOutputClearAllAction = new ClearOutputAction(fConsole);
		fOutputScrollLockAction = new ScrollLockAction(this, false);
		
		fInputDeleteAction = TextViewerAction.createDeleteAction(inputViewer);
		fMultiActionHandler.addAction(inputControl, ActionFactory.DELETE.getId(), fInputDeleteAction);
		
		fInputCutAction = TextViewerAction.createCutAction(inputViewer);
		fMultiActionHandler.addAction(inputControl, ActionFactory.CUT.getId(), fInputCutAction);
		
		fInputCopyAction = TextViewerAction.createCopyAction(inputViewer);
		fMultiActionHandler.addAction(inputControl, ActionFactory.COPY.getId(), fInputCopyAction);
		
		fInputPasteAction = TextViewerAction.createPasteAction(inputViewer);
		fMultiActionHandler.addAction(inputControl, ActionFactory.PASTE.getId(), fInputPasteAction);

		fInputSelectAllAction = TextViewerAction.createSelectAllAction(inputViewer);
		fMultiActionHandler.addAction(inputControl, ActionFactory.SELECT_ALL.getId(), fInputSelectAllAction);

        ResourceBundle bundle = SharedMessages.getCompatibilityBundle();
		fFindReplaceAction = new FindReplaceAction(bundle, "FindReplaceAction_", fConsoleView); //$NON-NLS-1$
		fFindReplaceAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.FIND_REPLACE);
		fMultiActionHandler.addAction(outputControl, ActionFactory.FIND.getId(), fFindReplaceAction);
		fMultiActionHandler.addAction(inputControl, ActionFactory.FIND.getId(), fFindReplaceAction);
		fOutputViewer.getDocument().addDocumentListener(new FindReplaceUpdater());
		inputViewer.getDocument().addDocumentListener(new FindReplaceUpdater());

		inputViewer.addSelectionChangedListener(fMultiActionHandler);
		fOutputViewer.addSelectionChangedListener(fMultiActionHandler);
	}
	
	private void hookContextMenu() {
		
		String idBase = fConsole.getType();
		
		String id = idBase + "#OutputContextMenu";
		MenuManager menuMgr = new MenuManager("#OutputContextMenu", id);
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillOutputContextMenu(manager);
			}
		});
		Control control = fOutputViewer.getControl();
		Menu menu = menuMgr.createContextMenu(control);
		control.setMenu(menu);
		getSite().registerContextMenu(id, menuMgr, fOutputViewer);
		
		id = idBase + "#InputContextMenu";
		menuMgr = new MenuManager("#InputContextMenu", id);
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillInputContextMenu(manager);
			}
		});
		control = fInputGroup.getSourceViewer().getControl();
		menu = menuMgr.createContextMenu(control);
		control.setMenu(menu);
		getSite().registerContextMenu(id, menuMgr, fInputGroup.getSourceViewer());
	}
	
	protected void hookDND() {
		
		DNDUtil.addDropSupport(fOutputViewer, 
				new SubmitDropAdapter(this), DND.DROP_COPY, 
				new Transfer[] { TextTransfer.getInstance() } );
	}
	
	protected void contributeToActionBars() {
		
		IActionBars bars = getSite().getActionBars();
		
		fMultiActionHandler.registerGlobalActions(bars);
		
		IToolBarManager toolBar = bars.getToolBarManager();
		toolBar.appendToGroup(IConsoleConstants.OUTPUT_GROUP, fOutputClearAllAction);
		toolBar.appendToGroup(IConsoleConstants.OUTPUT_GROUP, fOutputScrollLockAction);
	}
	
	protected void fillInputContextMenu(IMenuManager manager) {

		manager.add(fInputCutAction);
		manager.add(fInputCopyAction);
		manager.add(fInputPasteAction);
		
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	protected void fillOutputContextMenu(IMenuManager manager) {

		manager.add(fOutputCopyAction);
		manager.add(fOutputSelectAllAction);
		
		manager.add(new Separator("more"));
		manager.add(fFindReplaceAction);
//		manager.add(new FollowHyperlinkAction(fViewer));

		manager.add(new Separator("submit"));
		manager.add(fOutputPasteAction);
		
		manager.add(new Separator("view"));
		manager.add(fOutputClearAllAction);
		manager.add(fOutputScrollLockAction);
		
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	public void dispose() {

		fConsole.removePropertyChangeListener(this);
		
		if (fOutputViewer != null) {
			fOutputViewer.removeSelectionChangedListener(fMultiActionHandler);
			fInputGroup.getSourceViewer().removeSelectionChangedListener(fMultiActionHandler);
			fMultiActionHandler.dispose();
		}
		fMultiActionHandler = null;
		
		fFindReplaceAction = null;
		
		fOutputCopyAction = null;
		fOutputPasteAction = null;
		fOutputSelectAllAction = null;
		fOutputClearAllAction = null;
		
		fInputDeleteAction = null;
		fInputCutAction = null;
		fInputCopyAction = null;
		fInputPasteAction = null;
		fInputSelectAllAction = null;
		
		fOutputViewer = null;
		fInputGroup.dispose();
		fInputGroup = null;
	}

	

	public IPageSite getSite() {
		
		return fSite;
	}
	
	public Control getControl() {
		
		return fControl;
	}
	
	public NIConsole getConsole() {
		
		return fConsole;
	}
	
	public Clipboard getClipboard() {
		
		return fClipboard;
	}
	
    public Object getAdapter(Class required) {
    	
		if (Widget.class.equals(required)) {
			if (fOutputViewer.getControl().isFocusControl())
				return fOutputViewer.getTextWidget();
			return fInputGroup.getSourceViewer().getTextWidget();
		}
   		if (IFindReplaceTarget.class.equals(required)) {
    		if (fInputGroup.getSourceViewer().getControl().isFocusControl())
    			return fInputGroup.getSourceViewer().getFindReplaceTarget();
   			return fOutputViewer.getFindReplaceTarget();
   		}
		return null;
    }
	
	public void setActionBars(IActionBars actionBars) {
		
//		fOutputViewer.setActionBars(actionBars);
	}

	public void setFocus() {
		
		fInputGroup.getSourceViewer().getControl().setFocus();
	}
	
	
	protected void onToolStop() {
		
		fOutputPasteAction.setEnabled(false);
		fInputGroup.getSubmitButton().setEnabled(false);
	}
	
	public void setAutoScroll(boolean enabled) {
		
		fOutputViewer.setAutoScroll(enabled);
		fOutputScrollLockAction.setChecked(!enabled);
	}
	
    public void propertyChange(PropertyChangeEvent event) {
    	
        if (Layouter.isOkToUse(fControl) ) {
			Object source = event.getSource();
			String property = event.getProperty();
			
			if (source.equals(fConsole) && IConsoleConstants.P_FONT.equals(property)) {
				Font font = fConsole.getFont();
				fOutputViewer.setFont(font);
				fInputGroup.setFont(font);
				fControl.layout();
			} 
			else if (IConsoleConstants.P_FONT_STYLE.equals(property)) {
				fControl.redraw();
			}
			else if (property.equals(IConsoleConstants.P_STREAM_COLOR)) {
				fOutputViewer.getTextWidget().redraw();
			} 
			else if (source.equals(fConsole) && property.equals(IConsoleConstants.P_TAB_SIZE)) {
			    int tabSize = ((Integer) event.getNewValue()).intValue();
			    fOutputViewer.setTabWidth(tabSize);
			    fInputGroup.getSourceViewer().setTabWidth(tabSize);
			} 
			else if (source.equals(fConsole) && property.equals(IConsoleConstants.P_CONSOLE_WIDTH)) {
				fOutputViewer.setConsoleWidth(fConsole.getConsoleWidth());
			}
		} 
	}
    
}