/*******************************************************************************
 * Copyright (c) 2006 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.ui.internal;

import org.eclipse.osgi.util.NLS;


public class Messages extends NLS {
	

	public static String LoadHistoryPage_title;
	public static String LoadHistoryPage_description;
	public static String LoadHistoryPage_FileTask_label;
	public static String SaveHistoryPage_AppendToFile_label;
	public static String SaveHistoryPage_OverwriteExisting_label;
	
	public static String SaveHistoryPage_title;
	public static String SaveHistoryPage_description;
	public static String SaveHistoryPage_Options_label;
	public static String SaveHistoryPage_FileTask_label;

	public static String LoadSaveHistoryPage_File_label;
	public static String LoadSaveHistoryPage_error_File_prefix;
	public static String LoadSaveHistoryPage_Encoding_label;
	
	public static String Console_SubmitButton_label;

	private static final String BUNDLE_NAME = Messages.class.getName();
	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
	private Messages() {}
}
