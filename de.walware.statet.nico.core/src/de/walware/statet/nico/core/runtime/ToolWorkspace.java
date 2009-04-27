/*******************************************************************************
 * Copyright (c) 2006-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.core.runtime;

import java.util.List;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;

import de.walware.ecommons.FileUtil;

import de.walware.statet.nico.core.NicoCore;
import de.walware.statet.nico.core.runtime.ToolController.IToolStatusListener;


/**
 * It belongs to a ToolProcess and has the same life cycle.
 */
public class ToolWorkspace {
	
	protected class ControllerListener implements IToolStatusListener {
		
		public void controllerStatusRequested(final ToolStatus currentStatus, final ToolStatus requestedStatus, final List<DebugEvent> eventCollection) {
		}
		
		public void controllerStatusRequestCanceled(final ToolStatus currentStatus, final ToolStatus requestedStatus, final List<DebugEvent> eventCollection) {
		}
		
		public void controllerStatusChanged(final ToolStatus oldStatus, final ToolStatus newStatus, final List<DebugEvent> eventCollection) {
			// by definition in tool lifecycle thread
			if (isPublishPromptStatus(newStatus)) {
				if (fCurrentPrompt == null || fCurrentPrompt == fPublishedPrompt) {
					return;
				}
				fPublishedPrompt = fCurrentPrompt;
				firePrompt(fCurrentPrompt, eventCollection);
				return;
			}
			else {
				fPublishedPrompt = fDefaultPrompt;
				firePrompt(fDefaultPrompt, eventCollection);
			}
		}
		
	}
	
	
	public static final int DETAIL_PROMPT = 1;
	public static final int DETAIL_LINE_SEPARTOR = 2;
	
	private volatile String fLineSeparator;
	private volatile String fFileSeparator;
	
	private volatile Prompt fCurrentPrompt;
	private volatile Prompt fDefaultPrompt;
	private Prompt fPublishedPrompt;
	
	private IFileStore fWorkspaceDir;
	
	private String fRemoteHost;
	private IPath fRemoteWorkspaceDir;
	
	
	public ToolWorkspace(final ToolController controller,
			Prompt prompt, final String lineSeparator,
			final String remoteHost) {
		if (prompt == null) {
			prompt = Prompt.DEFAULT;
		}
		fPublishedPrompt = fCurrentPrompt = fDefaultPrompt = prompt;
		fRemoteHost = remoteHost;
		controlSetLineSeparator(lineSeparator);
		controlSetFileSeparator(null);
		
		controller.addToolStatusListener(createToolStatusListener());
	}
	
	protected IToolStatusListener createToolStatusListener() {
		return new ControllerListener();
	}
	
	
	protected void refreshFromTool(final IProgressMonitor monitor) throws CoreException {
	}
	
	public final String getLineSeparator() {
		return fLineSeparator;
	}
	
	public final String getFileSeparator() {
		return fFileSeparator;
	}
	
	
	public Prompt getPrompt() {
		return fPublishedPrompt;
	}
	
	public final Prompt getDefaultPrompt() {
		return fDefaultPrompt;
	}
	
	public final IFileStore getWorkspaceDir() {
		return fWorkspaceDir;
	}
	
	public String getEncoding() {
		return "UTF-8"; //$NON-NLS-1$
	}
	
	
	public boolean isRemote() {
		return (fRemoteHost != null);
	}
	
	public String getRemoteAddress() {
		return fRemoteHost;
	}
	
	public IPath getRemoteWorkspaceDirPath() {
		return fRemoteWorkspaceDir;
	}
	
	public IFileStore toFileStore(final IPath toolPath) throws CoreException {
		if (fRemoteHost != null) {
			return NicoCore.mapRemoteResourceToFileStore(fRemoteHost, toolPath, fRemoteWorkspaceDir);
		}
		return FileUtil.getFileStore(toolPath.toString(), fWorkspaceDir);
	}
	
	public IFileStore toFileStore(final String toolPath) throws CoreException {
		if (fRemoteHost != null) {
			return NicoCore.mapRemoteResourceToFileStore(fRemoteHost, new Path(toolPath), fRemoteWorkspaceDir);
		}
		return FileUtil.getFileStore(toolPath, fWorkspaceDir);
	}
	
	public String toToolPath(final IFileStore fileStore) {
		if (fRemoteHost != null) {
			final IPath path = NicoCore.mapFileStoreToRemoteResource(fRemoteHost, fileStore);
			return (path != null) ? path.toString() : null;
		}
		return URIUtil.toPath(fileStore.toURI()).toOSString();
	}
	
	
	final void controlRefresh(final IProgressMonitor monitor) throws CoreException {
		refreshFromTool(monitor);
	}
	
	/**
	 * Use only in tool main thread.
	 * @param prompt the new prompt, null doesn't change anything
	 */
	final void controlSetCurrentPrompt(final Prompt prompt, final ToolStatus status) {
		if (prompt == fCurrentPrompt || prompt == null) {
			return;
		}
		fCurrentPrompt = prompt;
		if (isPublishPromptStatus(status)) {
			fPublishedPrompt = prompt;
			firePrompt(prompt, null);
		}
	}
	
	/**
	 * Use only in tool main thread.
	 * @param prompt the new prompt, null doesn't change anything
	 */
	final void controlSetDefaultPrompt(final Prompt prompt) {
		if (prompt == fDefaultPrompt || prompt == null) {
			return;
		}
		final Prompt oldDefault = fDefaultPrompt;
		fDefaultPrompt = prompt;
		if (oldDefault == fCurrentPrompt) {
			fCurrentPrompt = prompt;
		}
		if (oldDefault == fPublishedPrompt) {
			fPublishedPrompt = prompt;
			firePrompt(prompt, null);
		}
	}
	
	/**
	 * Use only in tool main thread.
	 * 
	 * The default separator is System.getProperty("line.separator") for local
	 * workspaces, and '\n' for remote workspaces.
	 * 
	 * @param newSeparator the new line separator, null sets the default separator
	 */
	final void controlSetLineSeparator(final String newSeparator) {
		final String oldSeparator = fLineSeparator;
		if (newSeparator != null) {
			fLineSeparator = newSeparator;
		}
		else {
			fLineSeparator = (fRemoteHost == null) ? System.getProperty("line.separator") : "\n"; //$NON-NLS-1$
		}
//		if (!fLineSeparator.equals(oldSeparator)) {
//			DebugEvent event = new DebugEvent(ToolWorkspace.this, DebugEvent.CHANGE, DETAIL_LINE_SEPARTOR);
//			event.setData(fLineSeparator);
//			fireEvent(event);
//		}
	}
	
	/**
	 * Use only in tool main thread.
	 * 
	 * The default separator is System.getProperty("file.separator") for local
	 * workspaces, and '/' for remote workspaces.
	 * 
	 * @param newSeparator the new file separator, null sets the default separator
	 */
	final void controlSetFileSeparator(final String newSeparator) {
		final String oldSeparator = fFileSeparator;
		if (newSeparator != null) {
			fFileSeparator = newSeparator;
		}
		else {
			fFileSeparator = (fRemoteHost == null) ? System.getProperty("file.separator") : "/"; //$NON-NLS-1$
		}
	}
	
	final void controlSetWorkspaceDir(final IFileStore directory) {
		fWorkspaceDir = directory;
	}
	
	final void controlSetRemoteWorkspaceDir(final IPath path) {
		fRemoteWorkspaceDir = path;
		try {
			controlSetWorkspaceDir(toFileStore(path));
		}
		catch (final CoreException e) {
			controlSetWorkspaceDir(null);
		}
	}
	
	
	private final boolean isPublishPromptStatus(final ToolStatus status) {
		return (status == ToolStatus.STARTED_IDLING || status == ToolStatus.STARTED_PAUSED);
	}
	
	private final void firePrompt(final Prompt prompt, final List<DebugEvent> eventCollection) {
		final DebugEvent event = new DebugEvent(ToolWorkspace.this, DebugEvent.CHANGE, DETAIL_PROMPT);
		event.setData(prompt);
		if (eventCollection != null) {
			eventCollection.add(event);
			return;
		}
		else {
			fireEvent(event);
		}
	}
	
	protected final void fireEvent(final DebugEvent event) {
		final DebugPlugin manager = DebugPlugin.getDefault();
		if (manager != null) {
			manager.fireDebugEventSet(new DebugEvent[] { event });
		}
	}
	
}
