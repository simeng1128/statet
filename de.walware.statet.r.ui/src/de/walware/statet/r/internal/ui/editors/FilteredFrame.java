/*******************************************************************************
 * Copyright (c) 2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.ui.editors;

import java.util.List;

import de.walware.ecommons.ltk.IElementName;
import de.walware.ecommons.ltk.IModelElement;
import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ltk.IModelElement.Filter;

import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRLangElement;


public class FilteredFrame implements IRFrame, IModelElement.Filter<IRLangElement> {
	
	
	private final IRFrame fFrame;
	private final ISourceUnit fExclude;
	
	
	public FilteredFrame(final IRFrame frame, final ISourceUnit exclude) {
		fFrame = frame;
		fExclude = exclude;
	}
	
	
	public String getFrameId() {
		return fFrame.getFrameId();
	}
	
	public int getFrameType() {
		return fFrame.getFrameType();
	}
	
	public IElementName getElementName() {
		return null;
	}
	
	public boolean hasModelChildren(final Filter<? super IRLangElement> filter) {
		return fFrame.hasModelChildren((fExclude != null) ? this : null);
	}
	
	public List<? extends IRLangElement> getModelChildren(final Filter filter) {
		return fFrame.getModelChildren((fExclude != null) ? this : null);
	}
	
	public List<? extends IRLangElement> getModelElements() {
		return fFrame.getModelElements();
	}
	
	public List<? extends IRFrame> getPotentialParents() {
		return fFrame.getPotentialParents();
	}
	
	
	public boolean include(final IRLangElement element) {
		final ISourceUnit su = element.getSourceUnit();
		return (su == null || !fExclude.getId().equals(su.getId()) );
	}
	
}
