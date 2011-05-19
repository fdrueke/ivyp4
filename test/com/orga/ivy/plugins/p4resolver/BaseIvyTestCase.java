/*
 * Copyright 2011 Felix Drueke
 * Copyright 2008 Last.fm
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.orga.ivy.plugins.p4resolver;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.ivy.ant.IvyPublish;
import org.apache.ivy.ant.IvyResolve;
import org.apache.ivy.ant.IvyRetrieve;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.junit.Before;


/**
 * Base class for test cases which emulate running Ivy as an Ant task.
 */
public abstract class BaseIvyTestCase extends BaseTestCase {

  // the pattern that Ivy should use to retrieve files *to*
  protected static final String DEFAULT_RETRIEVE_TO_PATTERN = TEST_TMP_PATH + "/[artifact].[ext]";

  // set cache location under "test/tmp" so it will get automatically cleaned between tests
  private File cache = new File(TEST_TMP_PATH + "/cache");

  /**
   * Folder holding various ivy.xml files used by the tests.
   */
  protected File ivysDataFolder = new File(baseTestDataFolder, "ivys");

  /**
   * Folder holding various ivysettings.xml files used by the tests.
   */
  protected File ivySettingsDataFolder = new File(baseTestDataFolder, "ivysettings");

  /**
   * Default ivy.xml file which can be used for retrieving artifacts.
   */
  protected File defaultIvyXml = new File(ivysDataFolder, "ivy-test-retrieve-default.xml");

  protected File defaultIvySettingsFile = new File(ivySettingsDataFolder, "ivysettings-default.xml");

  protected static final String DIST_PATH = TEST_TMP_PATH + "/build/dist";

  protected String defaultOrganisation = "testorg";

  protected String defaultModule = "testmodule";

  // set in ivy-test-publish.xml
  protected String defaultArtifactName = "testartifact.jar";

  protected String defaultIvyFileName = "ivy.xml";

  // ivy uses this folder for creating ivy.xml files for publish
  protected File tempDistFolder = new File(DIST_PATH);

  protected File defaultFileToPublish = new File(DIST_PATH + "/" + defaultArtifactName);

  protected String defaultFileContents = "testartifact 1.0";

  // property values for ant log level
  private static final String MSG_ERR = "MSG_ERR";
  private static final String MSG_WARN = "MSG_WARN";
  private static final String MSG_INFO = "MSG_INFO";
  private static final String MSG_VERBOSE = "MSG_VERBOSE";
  private static final String MSG_DEBUG = "MSG_DEBUG";

  @Before
  public void setUp() throws Exception {
    super.setUp();
    try {
    	cache.mkdirs();
    } catch (Exception e) {
    	e.printStackTrace();
    	throw e;
    }
    System.setProperty("ivy.cache.dir", cache.getAbsolutePath());
  }

  /**
   * Gets the value set in test.properties for Ant's message output level (defaults to MSG_DEBUG).
   * 
   * @return The value for Ant's message output level.
   */
  private int getAntMessageOutputLevel() {
    String logLevelString = TestProperties.getInstance().getProperty(TestProperties.PROPERTY_ANT_MESSAGE_OUTPUT_LEVEL,
        MSG_DEBUG).trim();
    int logLevel = Project.MSG_DEBUG;
    if (MSG_DEBUG.equals(logLevelString)) {
      logLevel = Project.MSG_DEBUG;
    } else if (MSG_VERBOSE.equals(logLevelString)) {
      logLevel = Project.MSG_VERBOSE;
    } else if (MSG_INFO.equals(logLevelString)) {
      logLevel = Project.MSG_INFO;
    } else if (MSG_WARN.equals(logLevelString)) {
      logLevel = Project.MSG_WARN;
    } else if (MSG_ERR.equals(logLevelString)) {
      logLevel = Project.MSG_ERR;
    } else {
      System.err.println("Undefined value for " + TestProperties.PROPERTY_ANT_MESSAGE_OUTPUT_LEVEL + " '"
          + logLevelString + "' " + MSG_DEBUG + " will be used.");
    }
    return logLevel;
  }

  /**
   * Creates a new Ant Project with logging set up.
   * 
   * @return A new Ant Project.
   */
  protected Project createProject() {
    Project project = new Project();
    // redirect ant output to System streams
    DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);
    consoleLogger.setMessageOutputLevel(getAntMessageOutputLevel());
    project.addBuildListener(consoleLogger);
    return project;
  }

  /**
   * Replaces all occurrences of an Ant property in the passed string.
   * 
   * @param sourceString The source string.
   * @param propertyName The name of the property.
   * @param replacementValue The replacement value for the property.
   * @return The sourceString, but with any Ant properties replaced by new values.
   */
  private String replaceAntProperty(String sourceString, String propertyName, String replacementValue) {
    return sourceString.replace("${" + propertyName + "}", replacementValue);
  }

  /**
   * Creates an Ivy settings file, replacing various values in the passed Ivy settings template file with values valid
   * for testing.
   * 
   * @param ivySettingsTemplate Ivy settings file containing Ant property placeholders for various values.
   * @return A File that can be used for the duration of a test method for Ivy settings.
   * @throws IOException If an error occurs reading the Ivy settings file or writing a new one to disk.
   */
  protected File prepareTestIvySettings(File ivySettingsTemplate) throws IOException {
    return prepareTestIvySettings(ivySettingsTemplate, null);
  }

  /**
   * Creates an Ivy settings file, replacing various values in the passed Ivy settings template file with values valid
   * for testing.
   * 
   * @param ivySettingsTemplate Ivy settings file containing Ant property placeholders for various values.
   * @param extraP4Attributes XML string which can be appended to the standard "p4" element.
   * @return A File that can be used for the duration of a test method for Ivy settings.
   * @throws IOException If an error occurs reading the Ivy settings file or writing a new one to disk.
   */
  protected File prepareTestIvySettings(File ivySettingsTemplate, String extraP4Attributes) throws IOException {
    String ivySettings = FileUtils.readFileToString(ivySettingsTemplate);
    ivySettings = replaceAntProperty(ivySettings, "ivy.repository.root", ivyRepositoryRoot);
    ivySettings = replaceAntProperty(ivySettings, TestProperties.PROPERTY_P4_USERNAME, p4UserName);
 //   ivySettings = replaceAntProperty(ivySettings, TestProperties.PROPERTY_P4_PASSWORD, p4Password);
    ivySettings = replaceAntProperty(ivySettings, TestProperties.PROPERTY_P4_PORT, p4Port);
    ivySettings = replaceAntProperty(ivySettings, TestProperties.PROPERTY_P4_HOST, p4Host);
    ivySettings = replaceAntProperty(ivySettings, TestProperties.PROPERTY_P4_REPOPATH, ivyRepositoryRoot);
    if (extraP4Attributes != null) {
      ivySettings = ivySettings.replace("><!--@-->", " " + extraP4Attributes + ">");
    }
    File tempIvySettingsFile = new File(testTempFolder, "ivysettings-test.xml");
    FileUtils.writeStringToFile(tempIvySettingsFile, ivySettings);
    return tempIvySettingsFile;
  }

  /**
   * Creates a test ivy.xml file, replacing the revision in the passed template file with the passed revision.
   * 
   * @param ivyFileTemplate Path to the template file.
   * @param revision Revision to use.
   * @return A test ivy.xml file.
   * @throws IOException If an error occurs reading the Ivy template file or writing a new one to disk.
   */
  protected File prepareTestIvyFile(File ivyFileTemplate, String revision) throws IOException {
	  return prepareTestIvyFile(ivyFileTemplate,"testmodule",revision);
  }
  
  /**
   * Creates a test ivy.xml file, replacing the revision in the passed template file with the passed revision.
   * 
   * @param ivyFileTemplate Path to the template file.
   * @param revision Revision to use.
   * @return A test ivy.xml file.
   * @throws IOException If an error occurs reading the Ivy template file or writing a new one to disk.
   */
  protected File prepareTestIvyFile(File ivyFileTemplate, String module, String revision) throws IOException {
    String ivyXML = FileUtils.readFileToString(ivyFileTemplate);
    ivyXML = ivyXML.replace("@rev", revision);
    ivyXML = ivyXML.replace("@module", module);
    
    File tempIvyFile = new File(testTempFolder, "ivy-test.xml");
    FileUtils.writeStringToFile(tempIvyFile, ivyXML);
    return tempIvyFile;
  }

  /**
   * Performs an Ivy resolve operation for the passed Ant Project.
   * 
   * @param project Ant project.
   * @param ivyFile Ivy File to use for resolving.
   */
  protected void resolve(Project project, File ivyFile) {
    IvyResolve resolve = new IvyResolve();
    resolve.setProject(project);
    resolve.setTaskName("resolve");
    // resolve.setOrganisation(defaultOrganisation);
    resolve.setFile(ivyFile);
    resolve.execute();
  }

  /**
   * Performs a retrieve operation using the default ivy settings and the passed ivy file and the default retrieve
   * pattern.
   * 
   * @param ivyFile The ivy file to use to determine what files to retrieve.
   * @throws IOException If an error occurs reading the default Ivy settings file.
   */
  protected void retrieve(File ivyFile) throws IOException {
    retrieve(ivyFile, DEFAULT_RETRIEVE_TO_PATTERN);
  }

  /**
   * Performs a retrieve operation using the default ivy settings and the passed ivy file and retrieve pattern.
   * 
   * @param ivyFile The ivy file to use to determine what files to retrieve.
   * @param retrieveToPattern Pattern to retrieve files to.
   * @throws IOException If an error occurs reading the default Ivy settings file.
   */
  protected void retrieve(File ivyFile, String retrieveToPattern) throws IOException {
    File ivySettingsFile = prepareTestIvySettings(defaultIvySettingsFile);
    retrieve(ivyFile, retrieveToPattern, ivySettingsFile);
  }

  /**
   * Performs a retrieve operation using the passed ivy file, retrieve pattern and ivy settings file.
   * 
   * @param ivyFile The ivy file to use to determine what files to retrieve.
   * @param retrieveToPattern Pattern to retrieve files to.
   * @param ivySettingsFile The ivy settings file.
   * @throws IOException If an error occurs reading the default Ivy settings file.
   */
  protected void retrieve(File ivyFile, String retrieveToPattern, File ivySettingsFile) throws IOException {
    Project project = createProject();
    IvyRetrieve retrieve = new IvyRetrieve();
    retrieve.setProject(project);
    retrieve.setTaskName("retrieve");
    retrieve.setPattern(retrieveToPattern);

    project.setProperty("ivy.settings.file", ivySettingsFile.getAbsolutePath());
    resolve(project, ivyFile);
    retrieve.execute();
  }

  /**
   * Creates an IvyPublish object filled in with default values.
   * 
   * @param revision Revision to be published.
   * @param overwrite Overwrite value to set on publish operation (if null no value will be set and default will be
   *          used).
   * @return An IvyPublish object.
   */
  protected IvyPublish createIvyPublish(String revision, Boolean overwrite) {
    IvyPublish ivyPublish = new IvyPublish();
    ivyPublish.setTaskName("publish");
    ivyPublish.setArtifactspattern(DIST_PATH + "/[artifact].[ext]");
    ivyPublish.setOrganisation(defaultOrganisation);
    ivyPublish.setModule(defaultModule);
    ivyPublish.setResolver("ivyp4");
    ivyPublish.setPubrevision(revision);
    if (overwrite != null) {
      ivyPublish.setOverwrite(overwrite);
    }
    return ivyPublish;
  }

  /**
   * Performs a publish operation. File(s) to be published must exist before this is called.
   * 
   * @param ivyFile Ivy file containing artifact(s) to publish.
   * @param ivySettingsFile Ivy settings file.
   * @param ivyPublish An initialised IvyPublish object.
   * @throws IOException If an error occurs writing the file contents to a File to be published.
   */
  protected void publish(File ivyFile, File ivySettingsFile, IvyPublish ivyPublish) throws IOException {
    Project project = createProject();
    project.setProperty("ivy.settings.file", ivySettingsFile.getAbsolutePath());
    ivyPublish.setProject(project);
    resolve(project, ivyFile);
    ivyPublish.execute();
    FileUtils.deleteDirectory(tempDistFolder);
  }

  /**
   * Performs a publish operation.
   * 
   * @param ivySettingsFile Ivy settings file.
   * @param artifactFileContents String contents to be published as artifact file.
   * @param ivyPublish An initialised IvyPublish object.
   * @throws IOException If an error occurs writing the file contents to a File to be published.
   */
  protected void publish(File ivySettingsFile, String artifactFileContents, IvyPublish ivyPublish) throws IOException {
    File defaultIvyPublishFile = new File(ivysDataFolder, "ivy-test-publish.xml");
    FileUtils.writeStringToFile(defaultFileToPublish, artifactFileContents);
    publish(defaultIvyPublishFile, ivySettingsFile, ivyPublish);
  }

  /**
   * Performs a publish operation.
   * 
   * @param ivySettingsFile Ivy settings file.
   * @param artifactFileContents String contents to be published as artifact file.
   * @param revision Revision to be published.
   * @param overwrite Overwrite value to set on publish operation (if null no value will be set and default will be
   *          used).
   * @throws IOException If an error occurs writing the file contents to a File to be published.
   */
  protected void publish(File ivySettingsFile, String artifactFileContents, String revision, Boolean overwrite)
    throws IOException {
    IvyPublish publish = createIvyPublish(revision, overwrite);
    publish(ivySettingsFile, artifactFileContents, publish);
  }

  /**
   * Performs a publish operation.
   * 
   * @param ivySettingsFile Ivy settings file.
   * @param artifactFileContents String contents to be published as artifacts file.
   * @param overwrite Overwrite value to set on publish operation (if null no value will be set and default will be
   *          used).
   * @throws IOException If an error occurs writing the file contents to a File to be published.
   */
  protected void publish(File ivySettingsFile, String artifactFileContents, Boolean overwrite) throws IOException {
    this.publish(ivySettingsFile, artifactFileContents, "1.0", overwrite);
  }

  /**
   * Performs a default publish operation.
   * 
   * @param ivySettingsFile Ivy settings file.
   * @param artifactFileContents String contents to be published as artifacts file.
   * @throws IOException If an error occurs writing the file contents to a File to be published.
   */
  protected void publish(File ivySettingsFile, String artifactFileContents) throws IOException {
    publish(ivySettingsFile, artifactFileContents, "1.0", null);
  }

  
    
}
