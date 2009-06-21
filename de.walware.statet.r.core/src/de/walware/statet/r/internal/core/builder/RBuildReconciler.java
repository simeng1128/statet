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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

import de.walware.ecommons.ICommonStatusConstants;
import de.walware.ecommons.ltk.IProblemRequestor;

import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.RProject;
import de.walware.statet.r.core.model.IManagableRUnit;
import de.walware.statet.r.core.model.IRClass;
import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRLangElement;
import de.walware.statet.r.core.model.IRMethod;
import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.core.rsource.ast.RAstNode;
import de.walware.statet.r.internal.core.sourcemodel.RModelIndex;
import de.walware.statet.r.internal.core.sourcemodel.RModelManager;
import de.walware.statet.r.internal.core.sourcemodel.RReconciler;


public class RBuildReconciler extends RReconciler {
	
	
	private RModelIndex fIndex;
	private final TaskMarkerHandler fTaskScanner;
	
	private MultiStatus fStatusCollector;
	
	
	public RBuildReconciler(final RModelManager manager) {
		super(manager);
		
		fIndex = manager.getIndex();
		fTaskScanner = new TaskMarkerHandler();
	}
	
	
	public void init(final RProject project, final MultiStatus status) throws CoreException {
		fTaskScanner.init(project);
		fStatusCollector = status;
	}
	
	/** for file build */
	public RUnitElement build(final IManagableRUnit su, final IProgressMonitor monitor) {
		final int type = (su.getModelTypeId().equals(RModel.TYPE_ID) ? su.getElementType() : 0);
		if (type == 0) {
			return null;
		}
		if (fStop) {
			throw new OperationCanceledException();
		}
		final Data data = new Data(su, monitor);
		
		updateAst(data, monitor);
		
		if (fStop) {
			throw new OperationCanceledException();
		}
		
		updateModel(data);
		
		if (fStop) {
			throw new OperationCanceledException();
		}
		
		final IProblemRequestor problemRequestor = su.getProblemRequestor();
//		if (problemRequestor != null) {
			initParseInput(data);
//			problemRequestor.beginReportingSequence();
			try {
				final List<RAstNode> comments = data.ast.root.getComments();
				fTaskScanner.setup(su.getResource());
				for (final RAstNode comment : comments) {
						final int offset = comment.getOffset()+1;
						fTaskScanner.checkForTasks(data.content.text.substring(offset, offset+comment.getLength()-1),
								offset, data.ast.getLineInformation());
				}
			}
			catch (final CoreException e) {
				fStatusCollector.add(new Status(IStatus.ERROR, RCore.PLUGIN_ID,
						ICommonStatusConstants.BUILD_ERROR, "Failed to create task marker(s).", e));
			}
//			f2SyntaxReporter.run(su, ast, problemRequestor);
//			problemRequestor.endReportingSequence();
//		}
		
		if (fStop) {
			throw new OperationCanceledException();
		}
		return collectExports(data);
	}
	
	private RUnitElement collectExports(final Data data) {
		if (data.newModel == null) {
			return null;
		}
		
		final IRFrame topFrame = data.newModel.getTopFrame();
		final List<? extends IRLangElement> children = topFrame.getModelChildren(null);
		if (children == null || children.size() == 0) {
			return null;
		}
		final ArrayList<IRLangElement> exports = new ArrayList<IRLangElement>(children.size());
		final RUnitElement root = new RUnitElement(data.su, exports);
		for (final IRLangElement element : children) {
			final int type = element.getElementType();
			switch (type & IRLangElement.MASK_C1) {
			case IRLangElement.C1_METHOD:
				exports.add(new ExportedRMethod(root, (IRMethod) element));
				break;
			case IRLangElement.C1_CLASS:
				exports.add(new ExportedRClass(root, (IRClass) element));
				break;
			case IRLangElement.C1_VARIABLE:
				exports.add(new ExportedRElement(root, element));
				break;
			default:
				continue;
			}
		}
		return root;
	}
	
}
