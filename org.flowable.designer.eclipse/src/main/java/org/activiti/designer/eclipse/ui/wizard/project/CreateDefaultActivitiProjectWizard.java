/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.designer.eclipse.ui.wizard.project;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.activiti.designer.eclipse.common.ActivitiPlugin;
import org.activiti.designer.eclipse.common.PluginImage;
import org.activiti.designer.util.ActivitiConstants;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

public class CreateDefaultActivitiProjectWizard extends BasicNewProjectResourceWizard {

  private static final String BASIC_NEW_PROJECT_PAGE_NAME = "basicNewProjectPage";

  @Override
  public void createPageControls(Composite pageContainer) {

    super.createPageControls(pageContainer);

    // Set properties on the basicNewProjectPage
    final WizardNewProjectCreationPage basicNewProjectPage = getBasicNewProjectPage();
    if (basicNewProjectPage != null) {
      basicNewProjectPage.setTitle("Create a Flowable Project");
      basicNewProjectPage.setImageDescriptor(ActivitiPlugin.getImageDescriptor(PluginImage.FLOWABLE_LOGO_64x64));
      basicNewProjectPage.setDescription("Create an Flowable Project in the workspace.");
    }

  }

  @Override
  public boolean performFinish() {
    if (!super.performFinish()) {
      return false;
    }

    IProject newProject = getNewProject();

    try {

      IProjectDescription description = newProject.getDescription();
      String[] newNatures = new String[2];
      newNatures[0] = JavaCore.NATURE_ID;
      newNatures[1] = ActivitiConstants.NATURE_ID;
      description.setNatureIds(newNatures);
      newProject.setDescription(description, null);

      IJavaProject javaProject = JavaCore.create(newProject);

      createSourceFolders(newProject);
      createOutputLocation(javaProject);

      IFile pomFile = newProject.getFile("pom.xml");
      InputStream pomSource = new ByteArrayInputStream(createPOMFile().getBytes());
      pomFile.create(pomSource, true, null);
      pomSource.close();

      String[] userLibraryNames = JavaCore.getUserLibraryNames();
      boolean activitiExtensionLibraryPresent = false;
      if (userLibraryNames != null && userLibraryNames.length > 0) {
        for (String userLibraryName : userLibraryNames) {
          if (ActivitiPlugin.USER_LIBRARY_NAME_EXTENSIONS.equals(userLibraryName)) {
            activitiExtensionLibraryPresent = true;
          }
        }
      }

      if (activitiExtensionLibraryPresent == false) {
        ClasspathContainerInitializer initializer = JavaCore.getClasspathContainerInitializer(JavaCore.USER_LIBRARY_CONTAINER_ID);
        IPath containerPath = new Path(JavaCore.USER_LIBRARY_CONTAINER_ID);
        initializer.requestClasspathContainerUpdate(containerPath.append(ActivitiPlugin.USER_LIBRARY_NAME_EXTENSIONS), null, new IClasspathContainer() {

          @Override
          public IPath getPath() {
            return new Path(JavaCore.USER_LIBRARY_CONTAINER_ID).append(ActivitiPlugin.USER_LIBRARY_NAME_EXTENSIONS);
          }

          @Override
          public int getKind() {
            return K_APPLICATION;
          }

          @Override
          public String getDescription() {
            return ActivitiPlugin.USER_LIBRARY_NAME_EXTENSIONS;
          }

          @Override
          public IClasspathEntry[] getClasspathEntries() {
            return new IClasspathEntry[] {};
          }
        });
      }

      IClasspathEntry[] entries = createClasspathEntries(javaProject);

      javaProject.setRawClasspath(entries, null);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  @Override
  public void setWindowTitle(final String newTitle) {
    if (null == newTitle || "".equals(newTitle))
    {
      super.setWindowTitle("New Flowable Project");
    }
    else
    {
      super.setWindowTitle(newTitle);
    }
  }

  /**
   * Gets the WizardNewProjectCreationPage from the Wizard, which is the first
   * page allowing the user to specify the project name and location.
   */
  protected WizardNewProjectCreationPage getBasicNewProjectPage() {

    WizardNewProjectCreationPage result = null;

    final IWizardPage page = getPage(BASIC_NEW_PROJECT_PAGE_NAME);
    if (page != null && page instanceof WizardNewProjectCreationPage) {
      result = (WizardNewProjectCreationPage) page;
    }
    return result;
  }

  private IClasspathEntry[] createClasspathEntries(IJavaProject javaProject) {

    IPath srcPath1 = javaProject.getPath().append("src/main/java");
    IPath srcPath2 = javaProject.getPath().append("src/main/resources");
    IPath srcPath3 = javaProject.getPath().append("src/test/java");
    IPath srcPath4 = javaProject.getPath().append("src/test/resources");

    IPath[] javaPath = new IPath[] { new Path("**/*.java") };
    IPath testOutputLocation = javaProject.getPath().append("target/test-classes");

    IPath srcPathUserLibrary = new Path(ActivitiPlugin.DESIGNER_EXTENSIONS_USER_LIB_PATH);

    IClasspathEntry[] entries = { JavaCore.newSourceEntry(srcPath1, javaPath, null, null), JavaCore.newSourceEntry(srcPath2, javaPath),
        JavaCore.newSourceEntry(srcPath3, javaPath, null, testOutputLocation), JavaCore.newSourceEntry(srcPath4, javaPath, testOutputLocation),
        JavaRuntime.getDefaultJREContainerEntry(), JavaCore.newContainerEntry(srcPathUserLibrary) };

    return entries;
  }

  private void createSourceFolders(IProject project) throws CoreException {

    List<String> sourceFolders = Collections.synchronizedList(new LinkedList<String>());

    sourceFolders.add("src");
    sourceFolders.add("src/main");
    sourceFolders.add("src/main/java");
    sourceFolders.add("src/main/resources/");
    sourceFolders.add(ActivitiConstants.DIAGRAM_FOLDER);
    sourceFolders.add("src/test/");
    sourceFolders.add("src/test/java/");
    sourceFolders.add("src/test/resources");

    for (String folder : sourceFolders) {
      IFolder sourceFolder = project.getFolder(folder);
      sourceFolder.create(false, true, null);
    }
  }

  private void createOutputLocation(IJavaProject javaProject) throws JavaModelException {

    IPath targetPath = javaProject.getPath().append("target/classes");
    javaProject.setOutputLocation(targetPath, null);
  }

  private String createPOMFile() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
    buffer.append("    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n");
    buffer.append("  <modelVersion>4.0.0</modelVersion>\n");
    buffer.append("  <groupId>org.flowable.examples</groupId>\n");
    buffer.append("  <artifactId>flowable-examples</artifactId>\n");
    buffer.append("  <version>1.0-SNAPSHOT</version>\n");
    buffer.append("  <packaging>jar</packaging>\n");
    buffer.append("  <name>BPMN 2.0 with Flowable</name>\n");
    buffer.append("  <properties>\n");
    buffer.append("    <flowable-version>5.22.0</flowable-version>\n");
    buffer.append("  </properties>\n");
    buffer.append("  <dependencies>\n");
    addDependency(buffer, "org.flowable", "flowable-engine", "${flowable-version}");
    addDependency(buffer, "org.flowable", "flowable-spring", "${flowable-version}");
    addDependency(buffer, "org.codehaus.groovy", "groovy-all", "2.4.3");
    addDependency(buffer, "com.h2database", "h2", "1.3.168");
    addDependency(buffer, "org.slf4j", "slf4j-api", "1.7.6");
    addDependency(buffer, "org.slf4j", "slf4j-jdk14", "1.7.6");
    addDependency(buffer, "junit", "junit", "4.11");
    buffer.append("  </dependencies>\n");
    buffer.append("	 <build>\n");
    buffer.append("    <plugins>\n");
    buffer.append("      <plugin>\n");
    buffer.append("        <groupId>org.apache.maven.plugins</groupId>\n");
    buffer.append("        <artifactId>maven-compiler-plugin</artifactId>\n");
    buffer.append("	       <version>2.3.2</version>\n");
    buffer.append("        <configuration>\n");
    buffer.append("	         <source>1.6</source>\n");
    buffer.append("	         <target>1.6</target>\n");
    buffer.append("	       </configuration>\n");
    buffer.append("	     </plugin>\n");
    buffer.append("	     <plugin>\n");
    buffer.append("        <groupId>org.apache.maven.plugins</groupId>\n");
    buffer.append("        <artifactId>maven-eclipse-plugin</artifactId>\n");
    buffer.append("        <inherited>true</inherited>\n");
    buffer.append("        <configuration>\n");
    buffer.append("	         <classpathContainers>\n");
    buffer.append("	           <classpathContainer>");
    buffer.append(ActivitiPlugin.DESIGNER_EXTENSIONS_USER_LIB_PATH);
    buffer.append("</classpathContainer>\n");
    buffer.append("	         </classpathContainers>\n");
    buffer.append("	       </configuration>\n");
    buffer.append("	     </plugin>\n");
    buffer.append("    </plugins>\n");
    buffer.append("	 </build>\n");
    buffer.append("</project>\n");
    return buffer.toString();
  }

  private void addDependency(StringBuilder buffer, String groupId, String artifactId, String version) {
    buffer.append("    <dependency>\n")
    		.append("      <groupId>")
    		.append(groupId)
    		.append("</groupId>\n")
    		.append("      <artifactId>")
    		.append(artifactId)
        .append("</artifactId>\n")
        .append("      <version>")
        .append(version)
        .append("</version>\n")
        .append("    </dependency>\n");
  }

}
