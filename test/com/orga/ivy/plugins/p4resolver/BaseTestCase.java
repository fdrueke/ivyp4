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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import junit.framework.AssertionFailedError;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;


/**
 * Base class for IvyP4 tests.
 */
public abstract class BaseTestCase {

	protected static final String TEST_PATH = "test";
	protected static final String TEST_TMP_PATH = TEST_PATH + "/tmp";
	protected static final String TEST_DATA_PATH = TEST_PATH + "/data";
	protected static final String TEST_CONF_PATH = TEST_PATH + "/conf";

	protected String p4UserName = TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_USERNAME);
	protected String p4Port = TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_PORT);
	protected String p4Host = TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_HOST);

	protected String ivyRepositoryPath = TestProperties.getInstance().getProperty(TestProperties.PROPERTY_P4_REPOPATH);

	/**
	 * The full p4 path to where the Ivy repository is located. This will be created by the tests.
	 */
	protected String ivyRepositoryRoot = ivyRepositoryPath;

	/**
	 * A temporary folder which tests can use to write data while they run, will be cleaned inbetween each test.
	 */
	protected File testTempFolder = new File(TEST_TMP_PATH);

	/**
	 * Folder containing test data.
	 */
	protected File baseTestDataFolder = new File(TEST_DATA_PATH);

	/**
	 * Controls whether the temp folder should be deleted between tests.
	 */
	protected boolean cleanupTempFolder = true;

	/**
	 * Controls whether the temp folder in the P4 repository should be deleted between tests.
	 */
	protected boolean cleanupP4 = true;

	protected P4Repository readRepository;

	@Before
	public void setUp() throws Exception {
		try {
			testTempFolder.delete();
			testTempFolder.mkdirs();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * Cleanup the temp folder which tests can use to write data.
	 * 
	 * @throws IOException
	 */
	@After
	public void cleanupTempFolder() throws IOException {
		if (cleanupTempFolder) {
			FileUtils.deleteDirectory(testTempFolder);
			if (testTempFolder.exists()) {
				Assert.fail("Failed to delete " + testTempFolder.getAbsolutePath());
			}
		}
	}


	protected void createDummyFile(String path, String name, String content) {
		Writer fw = null;

		File dir = new File(path);
		try 
		{ 
			if (dir.mkdirs() == false) {
				Assert.fail("Couldn't create create dir for dummy-file: " + path);
			}

			fw = new FileWriter( path + "/" + name ); 
			fw.write( content ); 
			//fw.append( System.getProperty("line.separator") ); // e.g. "\n" 
		} 
		catch ( IOException e ) { 
			Assert.fail("Couldn't create dummy-file " + path + "/" + name );
		} 
		finally { 
			if ( fw != null ) 
				try { fw.close(); } catch ( IOException e ) { 
					System.err.println( "Couldn't close dummy-file " +  path + "/" + name);
					Assert.fail("Couldn't close dummy-file " + path + "/" + name );
				} 
		}

	}

	/**
	 * Fails the test.
	 * 
	 * @param t Throwable that should be used as the reason for failing.
	 */
	protected void fail(Throwable t) {
		t.printStackTrace();
		throw new AssertionFailedError(t.getMessage());
	}

}
