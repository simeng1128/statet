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

package de.walware.statet.r.internal.sweave.processing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchDelegate;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchPage;

import de.walware.ecommons.FileValidator;
import de.walware.ecommons.ICommonStatusConstants;
import de.walware.ecommons.debug.core.OverlayLaunchConfiguration;
import de.walware.ecommons.debug.ui.LaunchConfigUtil;
import de.walware.ecommons.ui.util.UIAccess;

import net.sourceforge.texlipse.Texlipse;

import de.walware.statet.r.core.RUtil;
import de.walware.statet.r.debug.ui.launchconfigs.REnvTab;
import de.walware.statet.r.debug.ui.launchconfigs.RLaunchConfigurations;
import de.walware.statet.r.internal.sweave.Messages;
import de.walware.statet.r.internal.sweave.SweavePlugin;


public class RweaveTexLaunchDelegate extends LaunchConfigurationDelegate {
	
	
	public static final int STEP_WEAVE = 0x1;
	public static final int STEP_TEX = 0x2;
	public static final int STEP_PREVIEW = 0x4;
	
	public static final String SWEAVE_CONSOLE = "console"; //$NON-NLS-1$
	public static final String SWEAVE_LAUNCH = "cmdlaunch"; //$NON-NLS-1$
	
	public static final String PREVIEW_IDE = "ide"; //$NON-NLS-1$
	public static final String PREVIEW_SPECIAL = "tex"; //$NON-NLS-1$
	
	
	static ILaunchConfigurationDelegate getRunDelegate(final ILaunchConfiguration configuration) throws CoreException {
		final ILaunchConfigurationType type = configuration.getType();
		final Set<String> modes = new HashSet<String>();
		modes.add(ILaunchManager.RUN_MODE);
		ILaunchDelegate preferredDelegate = configuration.getPreferredDelegate(modes);
		if (preferredDelegate == null) {
			preferredDelegate = type.getPreferredDelegate(modes);
		}
		if (preferredDelegate != null) {
			return preferredDelegate.getDelegate();
		}
		final ILaunchDelegate[] delegates = type.getDelegates(modes);
		if (delegates.length > 0) {
			return delegates[0].getDelegate();
		}
		throw new CoreException(new Status(IStatus.ERROR, SweavePlugin.PLUGIN_ID, ICommonStatusConstants.LAUNCHING,
				"Missing Delegate", null)); //$NON-NLS-1$
	}
	
	
	static final String DEFAULT_CONSOLE_COMMAND = "Sweave(file = \"${resource_loc}\")"; //$NON-NLS-1$
	
	
	public RweaveTexLaunchDelegate() {
	}
	
	
	@Override
	protected IProject[] getBuildOrder(final ILaunchConfiguration configuration, final String mode) throws CoreException {
		final IResource resource = DebugUITools.getSelectedResource();
		if (resource != null) {
			return LaunchConfigUtil.getProjectList(resource);
		}
		return null;
	}
	
	public void launch(final ILaunchConfiguration configuration, final String mode,
			final ILaunch launch, final IProgressMonitor monitor) throws CoreException {
		LaunchConfigUtil.initProgressMonitor(configuration, monitor, 100);
		final int buildFlags = configuration.getAttribute(SweaveProcessing.ATT_BUILDSTEPS, 0);
		if (configuration instanceof ILaunchConfigurationWorkingCopy) {
			((ILaunchConfigurationWorkingCopy) configuration).setAttribute(SweaveProcessing.ATT_BUILDSTEPS, (String) null);
		}
		
		final IResource selectedResource = DebugUITools.getSelectedResource();
		final IWorkbenchPage workbenchPage = UIAccess.getActiveWorkbenchPage(false);
		IFile sweaveFile;
		if (selectedResource instanceof IFile && selectedResource.exists()) {
			sweaveFile = (IFile) selectedResource;
		}
		else {
			throw new CoreException(new Status(IStatus.ERROR, SweavePlugin.PLUGIN_ID, ICommonStatusConstants.LAUNCHCONFIG_ERROR,
					Messages.ProcessingConfig_error_NoFileSelected_message, null));
		}
		
		final RweaveTexTool thread = new RweaveTexTool(configuration.getName(), launch, workbenchPage, sweaveFile);
		
		// Tex config (for output format, before sweave)
		thread.fTexOpenEditor = configuration.getAttribute(TexTab.ATTR_OPENTEX_ENABLED, TexTab.OPEN_OFF);
		thread.fTexBuilderId = configuration.getAttribute(TexTab.ATTR_BUILDTEX_BUILDERID, -1);
		thread.fRunTex = configuration.getAttribute(TexTab.ATTR_BUILDTEX_ENABLED, false) && SweaveProcessing.isEnabled(STEP_TEX, buildFlags);
		thread.fConfiguredOutputDir = configuration.getAttribute(TexTab.ATTR_BUILDTEX_OUTPUTDIR, (String) null);
		
		// Sweave config
		final String sweaveProcessing = configuration.getAttribute(RweaveTab.ATTR_SWEAVE_ID, (String) null);
		if (sweaveProcessing.startsWith(RweaveTexLaunchDelegate.SWEAVE_LAUNCH)) {
			final String[] split = sweaveProcessing.split(":", 2); //$NON-NLS-1$
			final String sweaveConfigName = (split.length == 2) ? split[1] : ""; //$NON-NLS-1$
			
			final Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put(RLaunchConfigurations.ATTR_R_CMD_RESOURCE, sweaveFile.getLocation().toOSString());
			attributes.put(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, false);
			
			final ILaunchConfiguration sweaveConfig = getRCmdSweaveConfig(sweaveConfigName, attributes);
			if (sweaveConfig == null && SweaveProcessing.isEnabled(STEP_WEAVE, buildFlags)) {
				throw new CoreException(new Status(IStatus.ERROR, SweavePlugin.PLUGIN_ID, ICommonStatusConstants.LAUNCHCONFIG_ERROR,
						NLS.bind(Messages.ProcessingConfig_error_MissingRCmdConfig_message, sweaveConfigName), null));
			}
			thread.fSweaveConfig = sweaveConfig;
			
			final FileValidator workingDirectory = REnvTab.getWorkingDirectoryValidator(sweaveConfig, true);
			attributes.put(RLaunchConfigurations.ATTR_WORKING_DIRECTORY, workingDirectory.getFileStore().toURI().toString());
			final IStatus status = thread.setWorkingDir(workingDirectory.getFileStore(), (IContainer) workingDirectory.getWorkspaceResource(), false);
			if (status.getSeverity() >= IStatus.ERROR && SweaveProcessing.isEnabled(STEP_WEAVE, buildFlags)) {
				throw new CoreException(status);
			}
		}
		else if (sweaveProcessing.startsWith(RweaveTexLaunchDelegate.SWEAVE_CONSOLE)) {
			final String[] split = sweaveProcessing.split(":", 2); //$NON-NLS-1$
			final String command = (split.length == 2 && split[1].length() > 0) ? split[1] : DEFAULT_CONSOLE_COMMAND;
			thread.fSweaveCommands = VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(command, true);
			thread.fSweaveCommands = RUtil.escapeBackslash(thread.fSweaveCommands);
		}
		else {
			thread.setWorkingDir(null, sweaveFile.getParent(), true);
		}
		thread.fRunSweave = SweaveProcessing.isEnabled(RweaveTexLaunchDelegate.STEP_WEAVE, buildFlags);
		
		// Preview config
		final String preview = configuration.getAttribute(PreviewTab.ATTR_VIEWER_CODE, ""); //$NON-NLS-1$
		if ((RweaveTexLaunchDelegate.STEP_PREVIEW & buildFlags) != 0) {
			thread.fRunPreview = RweaveTexTool.EXPLICITE;
		}
		else if ((0xf & buildFlags) == 0 && preview.length() > 0) {
			thread.fRunPreview = RweaveTexTool.AUTO;
		}
		else {
			thread.fRunPreview = RweaveTexTool.NO;
		}
		if (thread.fRunPreview > RweaveTexTool.NO) {
			if (preview.startsWith(PREVIEW_SPECIAL)) {
				final String previewConfigName = preview.split(":", -1)[1]; //$NON-NLS-1$
				thread.fPreviewConfig = Texlipse.getViewerManager().getConfiguration(previewConfigName);
				if (thread.fPreviewConfig == null) {
					throw new CoreException(new Status(IStatus.ERROR, SweavePlugin.PLUGIN_ID, ICommonStatusConstants.LAUNCHCONFIG_ERROR,
							NLS.bind(Messages.ProcessingConfig_error_MissingViewerConfig_message, previewConfigName), null));
				}
			}
		}
		
		launch.addProcess(thread);
		thread.createJob().schedule();
//		thread.createThread().start();
//		if (!configuration.getAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, true)) {
//			try {
//				thread.join();
//			} catch (final InterruptedException e) {
//				Thread.interrupted();
//			}
//		}
	}
	
	private OverlayLaunchConfiguration getRCmdSweaveConfig(final String name, final Map<String, Object> attributes) throws CoreException {
		final ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		final ILaunchConfigurationType launchType = launchManager.getLaunchConfigurationType(RLaunchConfigurations.ID_R_CMD_CONFIGURATION_TYPE); //$NON-NLS-1
		final ILaunchConfiguration[] launchConfigurations = launchManager.getLaunchConfigurations(launchType);
		
		if (name != null && name.length() > 0) {
			for (final ILaunchConfiguration config : launchConfigurations) {
				if (config.getName().equals(name)) {
					return new OverlayLaunchConfiguration(config, attributes);
				}
			}
		}
		return null;
	}
	
}
