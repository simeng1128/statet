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

package de.walware.statet.r.internal.core.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import de.walware.ecommons.ltk.IElementName;
import de.walware.ecommons.ltk.IModelElement;
import de.walware.ecommons.ltk.ISourceUnit;

import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRLangElement;


public class RUnitElement implements IRLangElement, Serializable {
	
	
	private static final long serialVersionUID = 2909953007129363256L;
	
	
	public static RUnitElement read(final ISourceUnit su, final CompositeFrame envir, final InputStream input) throws IOException, ClassNotFoundException {
		final ObjectInputStream o = new ObjectInputStream(input);
		final RUnitElement element = (RUnitElement) o.readObject();
		element.fSourceUnit = su;
		element.fEnvir = envir;
		return element;
	}
	
	
	private transient ISourceUnit fSourceUnit;
	transient CompositeFrame fEnvir;
	List<IRLangElement> fElements;
	
	
	public RUnitElement(final ISourceUnit su, final List<IRLangElement> children) {
		fSourceUnit = su;
		fElements = Collections.unmodifiableList(children);
	}
	
	public RUnitElement() {
	}
	
	
	public String getModelTypeId() {
		return null;
	}
	
	public int getElementType() {
		return IModelElement.C2_SOURCE_FILE;
	}
	
	public IElementName getElementName() {
		return fSourceUnit.getElementName();
	}
	
	public String getId() {
		return fSourceUnit.getId();
	}
	
	public boolean exists() {
		return true;
	}
	
	public boolean isReadOnly() {
		return fSourceUnit.isReadOnly();
	}
	
	public ISourceUnit getSourceUnit() {
		return fSourceUnit;
	}
	
	public IRLangElement getModelParent() {
		return null;
	}
	
	public boolean hasModelChildren(final Filter filter) {
		return ExportedRElement.hasChildren(fElements, filter);
	}
	
	public List<? extends IRLangElement> getModelChildren(final Filter filter) {
		return ExportedRElement.getChildren(fElements, filter);
	}
	
	public Object getAdapter(final Class required) {
		if (IRFrame.class.equals(required)) {
			return fEnvir;
		}
		return null;
	}
	
	
	public void save(final OutputStream outputStream) throws IOException {
		final ObjectOutputStream o = new ObjectOutputStream(outputStream);
		o.writeObject(this);
		o.flush();
	}
	
}
