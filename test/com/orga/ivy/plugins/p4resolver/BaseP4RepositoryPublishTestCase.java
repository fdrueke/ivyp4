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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.ivy.util.Message;
import org.junit.Before;

import com.perforce.p4java.exception.ConfigException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.NoSuchObjectException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.ResourceException;
import com.perforce.p4java.option.UsageOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;



/**
 * Base class for test cases which test publish functionality.
 */
public abstract class BaseP4RepositoryPublishTestCase extends BaseIvyTestCase {
	
	private IOptionsServer server;
	
	@Before
	public void setUp() throws Exception {
		super.setUp();
		setUpRepository();
	}


	/**
	 * Sets up the repository by adding some dummy data.
	 * 
	 * @throws P4JavaException If an error occurs adding the default repository data to Perforce.
	 */
	private void setUpRepository() throws P4JavaException {
		String serverUriString = "p4java://" + p4Host + ":" + p4Port;

		try {
			server = ServerFactory.getOptionsServer(
					serverUriString, null,
					new UsageOptions(null).setProgramName("IvyP4ResolverTests")
					.setProgramVersion("0.0.0"));
		} catch (ConnectionException e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		} catch (NoSuchObjectException e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		} catch (ConfigException e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		} catch (ResourceException e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		} catch (URISyntaxException e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		}

		try {
			P4Utils.authenticate(server, serverUriString, p4UserName, null);
		} catch (Exception e) {
			Message.error("Perforce problem");
			e.printStackTrace();
			throw new P4JavaException("Perforce problem while setting up repository for unittests");
		}
	}

	/**
	 * Asserts all the effects of a publish action, using default values where necessary.
	 * 
	 * @param pubRevision The publication revision.
	 * @param artifactFileContents The expected artifact file contents.
	 * @throws P4JavaException If an error occurs checking the files in Perforce.
	 * @throws IOException If an error occurs reading the file contents.
	 */
	protected void assertPublish(String pubRevision, String artifactFileContents) throws P4JavaException, IOException {
		Map<String, String> defaultArtifacts = new HashMap<String, String>();
		defaultArtifacts.put(defaultArtifactName, artifactFileContents);
		this.assertPublish(defaultOrganisation, defaultModule, pubRevision, defaultArtifacts, defaultIvyFileName);
	}


	/**
	 * Asserts all the effects of a publish action, using default values where necessary.
	 * 
	 * @param pubRevision The publication revision.
	 * @param artifacts A map keyed by artifact name with values of the expected contents of the artifact files.
	 * @throws P4JavaException If an error occurs checking the files in Perforce.
	 * @throws IOException If an error occurs reading the file contents.
	 */
	protected void assertPublish(String pubRevision, Map<String, String> artifacts)
	throws P4JavaException, IOException {
		this.assertPublish(defaultOrganisation, defaultModule, pubRevision, artifacts, defaultIvyFileName);
	}

	/**
	 * Asserts all the effects of a publish action.
	 * 
	 * @param organisation The organisation.
	 * @param module The module.
	 * @param pubRevision The publication revision.
	 * @param artifacts A map keyed by artifact name with values of the expected contents of the artifact files.
	 * @param ivyFileName Expected published ivy file name.
	 * @throws P4JavaException If an error occurs checking the files in Perforce.
	 * @throws IOException If an error occurs reading the file contents.
	 */
	protected void assertPublish(String organisation, String module, String pubRevision, Map<String, String> artifacts,
			String ivyFileName) throws P4JavaException, IOException {
		String publishFolder =  TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH) + "/" + organisation + "/" + module + "/" + pubRevision + "/";
		assertPublication(publishFolder, artifacts, publishFolder, ivyFileName);
	}

	/**
	 * Asserts all the effects of a publish action under a single folder in Perforce.
	 * 
	 * @param artifactPublishFolder The folder in Perforce that artifact files should have been published to.
	 * @param artifacts A map keyed by artifact name with values of the expected contents of the artifact files.
	 * @param ivyPublishFolder The folder in Perforce that ivy files should have been published to.
	 * @param ivyFileName Expected published ivy file name.
	 * @throws P4JavaException If an error occurs checking the files in Perforce.
	 * @throws IOException If an error occurs reading the file contents.
	 */
	protected void assertPublication(String artifactPublishFolder, Map<String, String> artifacts,
			String ivyPublishFolder, String ivyFileName) throws P4JavaException, IOException {
		assertArtifactPublished(ivyPublishFolder, ivyFileName);
		for (Entry<String, String> artifact : artifacts.entrySet()) {
			String artifactName = artifact.getKey();
			String artifactFileContents = artifact.getValue();
			assertArtifactPublished(artifactPublishFolder, artifactName);
			File tempFile = new File(testTempFolder, "retrieved-" + artifactName);
			P4TestUtils.getFile(server,artifactPublishFolder + artifactName,tempFile);
			assertEquals(artifactFileContents, FileUtils.readFileToString(tempFile));
		}
	}

	/**
	 * Asserts that an artifact has been published to the repository, along with checksums.
	 * 
	 * @param publishFolder Folder that artifact should have been published to.
	 * @param artifactFileName The name of the artifact.
	 * @throws P4JavaException If an error occurs checking whether the artifact or its checksums exist.
	 */
	protected void assertArtifactPublished(String publishFolder, String artifactFileName) throws P4JavaException {
		if (!publishFolder.endsWith("/")) {
			publishFolder = publishFolder + "/";
		}
		assertTrue(publishFolder + artifactFileName + " doesn't exist", P4Utils.p4FileExists(server,publishFolder + artifactFileName));
		assertTrue(publishFolder + artifactFileName + ".sha1 doesn't exist", P4Utils.p4FileExists(server,publishFolder
				+ artifactFileName + ".sha1"));
		assertTrue(publishFolder + artifactFileName + ".md5 doesn't exist", P4Utils.p4FileExists(server,publishFolder
				+ artifactFileName + ".md5"));
	}

}
