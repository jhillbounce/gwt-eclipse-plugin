/*******************************************************************************
 * Copyright 2013 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.google.appengine.eclipse.wtp.facet;

import com.google.appengine.eclipse.core.properties.GaeProjectProperties;
import com.google.appengine.eclipse.wtp.AppEnginePlugin;
import com.google.appengine.eclipse.wtp.facet.ops.AppEngineApplicationXmlCreateOperation;
import com.google.appengine.eclipse.wtp.utils.ProjectUtils;
import com.google.gdt.eclipse.core.StatusUtilities;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jst.j2ee.internal.J2EEConstants;
import org.eclipse.jst.jee.project.facet.ICreateDeploymentFilesDataModelProperties;
import org.eclipse.jst.jee.project.facet.IEarCreateDeploymentFilesDataModelProperties;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.project.facet.core.IDelegate;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Creates deployment descriptors (appengine-application.xml, application.xml) upon facet install.
 */
@SuppressWarnings("restriction")
public final class GaeEarFacetInstallDelegate implements IDelegate {

  @Override
  public void execute(IProject project, IProjectFacetVersion fv, Object config,
      IProgressMonitor monitor) throws CoreException {
    IDataModel model = (IDataModel) config;
    try {
      IStatus status = new AppEngineApplicationXmlCreateOperation(model).execute(monitor, null);
      if (status != null && !status.isOK()) {
        throw new CoreException(status);
      }
      // ensure application.xml, ignoring GENERATE_DD property value in jst.ear data model
      ensureApplicationXml(ProjectUtils.getProject(model), monitor);
    } catch (ExecutionException e) {
      throw new CoreException(StatusUtilities.newErrorStatus(e, AppEnginePlugin.PLUGIN_ID));
    }
    // setup deployment options
    try {
      GaeProjectProperties.setGaeEnableJarSplitting(project,
          model.getBooleanProperty(IGaeFacetConstants.GAE_PROPERTY_ENABLE_JAR_SPLITTING));
      GaeProjectProperties.setGaeDoJarClasses(project,
          model.getBooleanProperty(IGaeFacetConstants.GAE_PROPERTY_DO_JAR_CLASSES));
      GaeProjectProperties.setGaeRetaingStagingDir(project,
          model.getBooleanProperty(IGaeFacetConstants.GAE_PROPERTY_RETAIN_STAGING_DIR));
    } catch (BackingStoreException e) {
      AppEnginePlugin.logMessage("Cannot setup deployment option", e);
    }
  }

  /**
   * Creates application.xml if necessary.
   */
  private void ensureApplicationXml(IProject project, IProgressMonitor monitor)
      throws ExecutionException {
    IVirtualComponent component = ComponentCore.createComponent(project, false);
    IVirtualFolder earroot = component.getRootFolder();
    IFile appXmlFile = earroot.getUnderlyingFolder().getFile(
        new Path(J2EEConstants.APPLICATION_DD_URI));
    // if the file exists, then it has been generated by jst.ear facet, and populated by it's
    // post-install action.
    if (!appXmlFile.exists()) {
      Class<?> dataModelClass = IEarCreateDeploymentFilesDataModelProperties.class;
      IDataModel dataModel = DataModelFactory.createDataModel(dataModelClass);
      dataModel.setProperty(ICreateDeploymentFilesDataModelProperties.TARGET_PROJECT, project);
      dataModel.getDefaultOperation().execute(monitor, null);
    }
  }
}
