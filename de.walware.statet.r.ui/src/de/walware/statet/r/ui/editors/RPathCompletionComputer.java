/*******************************************************************************
 * Copyright (c) 2007-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.ui.editors;

import java.util.List;
import java.util.Set;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import de.walware.ecommons.ui.text.sourceediting.ISourceEditor;
import de.walware.ecommons.ui.text.sourceediting.PathCompletionComputor;

import de.walware.statet.nico.core.ITool;
import de.walware.statet.nico.core.NicoCore;
import de.walware.statet.nico.core.runtime.IResourceMapping;
import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.core.runtime.ToolWorkspace;

import de.walware.statet.r.core.RUtil;
import de.walware.statet.r.core.rsource.IRDocumentPartitions;


/**
 * Completion computer for path in R code.
 * 
 * Supports workspace properties of a R tool process
 */
public class RPathCompletionComputer extends PathCompletionComputor {
	
	
	private ToolProcess<ToolWorkspace> fAssociatedTool;
	
	
	public RPathCompletionComputer() {
	}
	
	
	@Override
	public void sessionStarted(final ISourceEditor editor) {
		fAssociatedTool = (ToolProcess<ToolWorkspace>) editor.getAdapter(ITool.class);
		super.sessionStarted(editor);
	}
	
	@Override
	public void sessionEnded() {
		super.sessionEnded();
		fAssociatedTool = null;
	}
	
	@Override
	protected String getDefaultFileSeparator() {
		if (fAssociatedTool != null) {
			return fAssociatedTool.getWorkspaceData().getFileSeparator();
		}
		return super.getDefaultFileSeparator();
	}
	
	@Override
	protected IRegion getContentRange(final IDocument document, final int offset) throws BadLocationException {
		final ITypedRegion partition = TextUtilities.getPartition(document, IRDocumentPartitions.R_PARTITIONING, offset, true);
		int start = partition.getOffset();
		int end = partition.getOffset() + partition.getLength();
		if (start == end) {
			return null;
		}
		
		final char bound = document.getChar(start);
		if (bound == '\"' || bound == '\'') {
			start++;
		}
		else {
			return null;
		}
		if (start > offset) {
			return null;
		}
		if (end > start && document.getChar(end-1) == bound) {
			if (end == offset) {
				return null;
			}
			end--;
		}
		
		return new Region(start, end-start);
	}
	
	@Override
	protected IPath getRelativeBase() {
		if (fAssociatedTool != null) {
			final IFileStore wd = fAssociatedTool.getWorkspaceData().getWorkspaceDir();
			if (wd != null) {
				return URIUtil.toPath(wd.toURI());
			}
		}
		return null;
	}
	
	@Override
	protected IFileStore resolveStore(final IPath path) throws CoreException {
		if (fAssociatedTool != null) {
			return fAssociatedTool.getWorkspaceData().toFileStore(path);
		}
		return super.resolveStore(path);
	}
	
	@Override
	protected IStatus tryAlternative(final List<ICompletionProposal> matches, final IPath path,
			final int startOffset, final String startsWith, final String prefix, final String completionPrefix) throws CoreException {
		if (fAssociatedTool != null) {
			final String address = fAssociatedTool.getWorkspaceData().getRemoteAddress();
			if (address != null) {
				final boolean root = (path.segmentCount() == 0);
				final Set<IResourceMapping> mappings = NicoCore.getResourceMappingsFor(address);
				for (final IResourceMapping mapping : mappings) {
					final IPath remotePath = mapping.getRemotePath();
					if (remotePath.segmentCount() < path.segmentCount()) {
						continue;
					}
					if (root) {
						matches.add(new ResourceProposal(startOffset-prefix.length(), mapping.getFileStore(), remotePath.toString(), completionPrefix, null));
					}
					else {
						final int matching = path.matchingFirstSegments(remotePath);
						final int depth = path.segmentCount();
						if (matching > 0 || depth == 1) {
							final String next = remotePath.segment(matching + 1);
							if (next.startsWith(startsWith)) {
								// TODO
							}
						}
					}
				}
				return Status.OK_STATUS;
			}
		}
		return super.tryAlternative(matches, path, startOffset, startsWith, prefix, completionPrefix);
	}
	
	@Override
	protected String checkPrefix(final String prefix) {
		String unescaped = RUtil.unescapeCompletly(prefix);
		// keep a single (not escaped) backslash
		if (prefix.length() > 0 && prefix.charAt(prefix.length()-1) == '\\' && 
				(unescaped.length() == 0 || unescaped.charAt(unescaped.length()-1) != '\\')) {
			unescaped = unescaped + '\\';
		}
		return super.checkPrefix(unescaped);
	}
	
	@Override
	protected String checkPathCompletion(final IDocument document, final int completionOffset, String completion)
			throws BadLocationException {
		completion = RUtil.escapeCompletly(completion);
		int existingBackslashCount = 0;
		if (completionOffset >= 1) {
			if (document.getChar(completionOffset-1) == '\\') {
				existingBackslashCount++;
				if (completionOffset >= 2) {
					if (document.getChar(completionOffset-2) == '\\') {
						existingBackslashCount++;
					}
				}
			}
		}
		final boolean startsWithBackslash = (completion.length() >= 2 && 
				completion.charAt(0) == '\\' && completion.charAt(1) == '\\');
		if ((existingBackslashCount % 2) == 1) {
			if (startsWithBackslash) {
				completion = completion.substring(1);
			}
			else {
				completion = '\\' + completion;
			}
		}
		else if (existingBackslashCount > 0) {
			if (startsWithBackslash) {
				completion = completion.substring(2);
			}
		}
		return completion;
	}
	
	@Override
	protected boolean isWin() {
		if (fAssociatedTool != null && fAssociatedTool.getWorkspaceData().isRemote()) {
			return false;
		}
		return super.isWin();
	}
	
}
