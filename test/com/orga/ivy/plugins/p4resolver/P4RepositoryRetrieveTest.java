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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.apache.ivy.util.Message;
import org.apache.tools.ant.BuildException;
import org.junit.Before;
import org.junit.Test;

import com.perforce.p4java.exception.ConfigException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.NoSuchObjectException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.ResourceException;
import com.perforce.p4java.option.UsageOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;

/**
 * Tests "ivy retrieve" calls on the P4Repository.
 */
public class P4RepositoryRetrieveTest extends BaseIvyTestCase {

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
	private void setUpRepository() throws Exception {
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

		createDummyFile(testTempFolder + "/" + "acme/widgets/4.5", "widgets.jar" , "acme widgets 4.5");
		createDummyFile(testTempFolder + "/" + "acme/widgets/4.4", "widgets.jar" , "acme widgets 4.4");
		createDummyFile(testTempFolder + "/" + "acme/gizmos/1.0", "gizmos.jar" , "acme gizmos 1.0");
		createDummyFile(testTempFolder + "/" + "constructus/toolkit/1.1", "toolkit.jar" , "constructus toolkit 1.1");

		try {
			P4TestUtils.put(server,new File(testTempFolder + "/" + "acme/widgets/4.5" + "/" + "widgets.jar"), 
					TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH) + "/acme/widgets/4.5/widgets.jar", true);
			P4TestUtils.put(server,new File(testTempFolder + "/" + "acme/widgets/4.4" + "/" + "widgets.jar"), 
					TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH) + "/acme/widgets/4.4/widgets.jar", true);
			P4TestUtils.put(server,new File(testTempFolder + "/" + "acme/gizmos/1.0" + "/" + "gizmos.jar"), 
					TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH) + "/acme/gizmos/1.0/gizmos.jar", true);
			P4TestUtils.put(server,new File(testTempFolder + "/" + "constructus/toolkit/1.1" + "/" + "toolkit.jar"), 
					TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH) + "/constructus/toolkit/1.1/toolkit.jar", true);
		} catch (Exception e) { 
			Message.error("Error putting test-files to perforce");
			e.printStackTrace();
			throw e;
		}
	}

	@Test
	public void testRetrieve() throws IOException {
		retrieve(new File(ivysDataFolder, "ivy-test-retrieve.xml"));
		assertEquals("acme widgets 4.4", FileUtils.readFileToString(new File(testTempFolder, "widgets.jar")));
	}

	@Test
	public void testRetrieveLatestIntegration() throws IOException {
		retrieve(new File(ivysDataFolder, "ivy-test-retrieve-latest-integration.xml"));
		// latest.integration should resolve to 4.5
		assertEquals("acme widgets 4.5", FileUtils.readFileToString(new File(testTempFolder, "widgets.jar")));
		// 1.1 should have been resolved directly
		assertEquals("constructus toolkit 1.1", FileUtils.readFileToString(new File(testTempFolder, "toolkit.jar")));
	}


	@Test(expected = BuildException.class)
	public void testRetrieve_NonExistent() throws IOException {
		// this ivy xml points to a file which doesn't exist
		retrieve(new File(ivysDataFolder, "ivy-test-retrieve-dependent.xml"));
	}

	@Test
	public void testRetrieve_Patt1() throws IOException, P4JavaException {
		File ivySettingsFile = prepareTestIvySettings(new File(ivySettingsDataFolder, "ivysettings-patt1.xml"));
		// easier to just do a publish rather than create files, folders by hand
		publish(ivySettingsFile, defaultFileContents, "1.0", false);
		String fileContents2 = "testartifact 2.0";
		publish(ivySettingsFile, fileContents2, "2.0", false);

		retrieve(new File(ivysDataFolder, "ivy-test-retrieve-patt1.xml"), DEFAULT_RETRIEVE_TO_PATTERN, ivySettingsFile);
		assertEquals(fileContents2, FileUtils.readFileToString(new File(testTempFolder, "testartifact.jar")));
	}

	@Test
	public void testRetrieve_RevPlus() throws IOException, P4JavaException {
		retrieve(new File(ivysDataFolder, "ivy-test-retrieve-revplus.xml"));
		assertEquals("acme widgets 4.5", FileUtils.readFileToString(new File(testTempFolder, "widgets.jar")));
	}

}
