/*
 * Copyright 2011 Felix Drueke
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.util.Message;

import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConfigException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.NoSuchObjectException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.exception.ResourceException;
import com.perforce.p4java.option.UsageOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;


/**
 *
 * Ivy repository that uses Perforce for artifact storage.
 * 
 * @author Felix Drueke
 *
 */
public class P4Repository extends AbstractRepository {

	/**
	 * Port number that P4 server is listening on.
	 */
	private String p4Port;

	/**
	 * Host number that P4 server is listening on.
	 */
	private String p4Host;

	/**
	 * The user name to use to connect to Perforce.
	 */
	private String p4User;

	/**
	 * The password to use to connect to Perforce. 
	 * (not necessarily required if we're already authenticated via a valid p4ticket)
	 */
	private String p4Passwd;

	/**
	 * Server object 
	 */
	private IOptionsServer server;

	/**
	 * Remember if we're authenticated.
	 */
	private Boolean authenticated = false;

	/**
	 * map of resources for that we got a "getResource" request
	 */
	private Map<String, Resource> resourcesCache = new HashMap<String, Resource>();

	/**
	 * ModuleRevisionId for a new publication as given by Ivy
	 */
	private ModuleRevisionId moduleRevisionId;

	/**
	 * The p4 transaction for putting files.
	 */
	private P4PublishTransaction publishTransaction;
	
	/**
	 * prefix of p4java uris
	 */
	private static final String p4UriPrefix = "p4java://";


	/**
	 * Initialises p4-repository to accept requests
	 */
	public P4Repository() {
		super();

		try {
			Manifest manifest = getManifest(this.getClass());
			Attributes attributes = manifest.getMainAttributes();
			Message.info("IvyP4 Build-Version: " + attributes.getValue("Build-Version"));
			Message.info("IvyP4 Build-DateTime: " + attributes.getValue("Build-DateTime"));
		} catch (IOException e) {
			Message.warn("Could not load manifest: " + e.getMessage());
		}

	}

	/**
	 * Creates a p4-server-object and tries to authenticate
	 * 
	 * @return true if authentication was successful
	 * @throws IOException If connection data is incomplete
	 */
	private boolean authenticate() throws IOException {
		String serverUriString = null;

		// check whether host/port are set - otherwise try to get them from env
		if ((p4Host == null) || (p4Port == null)) {
			Message.debug("host or port not set via properties - trying environment-variable");

			String hostport = System.getenv("P4PORT");

			if (hostport == null) {
				Message.error("Perforce host&port could not be determined!");
				throw new IOException("Can't determine your perforce server");
			} else {
				Message.debug("Found out that your p4server is " + hostport + " via the environment-variable P4PORT");

				serverUriString = p4UriPrefix + hostport;
			}
		} else {
			serverUriString = p4UriPrefix + p4Host + ":" + p4Port;
		}

		try {
			server = ServerFactory.getOptionsServer(
					serverUriString, null,
					new UsageOptions(null).setProgramName("IvyP4Resolver")
					.setProgramVersion("0.0.1"));
		} catch (ConnectionException e) {
			Message.error("Can't connect to perforce-server '" + serverUriString + "'");
			e.printStackTrace();
		} catch (NoSuchObjectException e) {
			Message.error("Perforce-problem - don't know how to handle '" + serverUriString + "'");
			e.printStackTrace();
		} catch (ConfigException e) {
			Message.error("Perforce-problem - wrong configuration '" + serverUriString + "'");
			e.printStackTrace();
		} catch (ResourceException e) {
			Message.error("Insufficient ressources while trying to connect to perforce");
			e.printStackTrace();
		} catch (URISyntaxException e) {
			Message.error("Perforce-problem - wrong uri '" + serverUriString + "'");
			e.printStackTrace();
		}
		if (server != null) { 
			try {
				authenticated = P4Utils.authenticate(server, serverUriString, p4User, p4Passwd);
			} catch (Exception e) {
				Message.error("Perforce authentication failed");
				e.printStackTrace();
			}
			return authenticated;
		} else {
			return false;
		}
	}

	/**
	 * Handles a request to retrieve a file from the repository.
	 * 
	 * @param source Path to the resource to retrieve in perforce depot notation
	 * @param destination The location where the file should be retrieved to.
	 * @throws IOException If an error occurs retrieving the file.
	 */
	public void get(String source, File destination) throws IOException {
		fireTransferInitiated(getResource(source), TransferEvent.REQUEST_GET);
		Message.debug("Getting source "+source+" to destination "+destination.getName());

		if (!authenticated) {
			authenticate();
		}

		String[] filePaths = new String[1];
		filePaths[0] = source;
		FileOutputStream destStream = null;

		try {
			List<IFileSpec> fileList = server.getDepotFiles(
					FileSpecBuilder.makeFileSpecList(filePaths), false);

			InputStream p4Content = server.getFileContents(fileList, false, true);
			destStream = new FileOutputStream( destination ); 

			byte[] buffer = new byte[ 0xFFFF ]; 
			for ( int len; (len = p4Content.read(buffer)) != -1; ) 
				destStream.write( buffer, 0, len ); 
			destStream.close();
		} catch (P4JavaException e) {
			Message.error("\tcouldn't get file " + source);
			e.printStackTrace();
		} catch ( IOException e ) { 
			Message.error("\tproblem writing to destination");
			e.printStackTrace(); 
		} finally {
			if (destStream != null) { destStream.close(); }
		}
	}

	/**
	 * Handles a request to add/update a file to/in the repository.
	 * 
	 * @param source The source file.
	 * @param destination The location of the file in the repository.
	 * @param overwrite Whether to overwrite the file if it already exists.
	 * @throws IOException If an error occurs putting a file (invalid path, invalid login credentials etc.)
	 */
	public void put(File source, String destination, boolean overwrite) throws IOException {
		fireTransferInitiated(getResource(destination), TransferEvent.REQUEST_PUT);
		Message.debug("Putting source "+source.getName()+" to destination "+destination);

		if (!authenticated) {
			authenticate();
		}

		// open the file for 'add' to the temporary client
		try {
			if (publishTransaction == null) { // haven't initialised transaction on a previous put
				// create a new temporary client for publishing 
				publishTransaction = new P4PublishTransaction(server,destination,moduleRevisionId);
			}
			publishTransaction.addPutOperation(server, source, destination, overwrite);
		} catch (ConnectionException e) {
			e.printStackTrace();
			throw new IOException("Connection-problem while adding files to perforce");
		} catch (AccessException e) {
			e.printStackTrace();
			throw new IOException("Access-problem while adding files to perforce");
		} catch (RequestException e) {
			e.printStackTrace();
			throw new IOException("Request-problem while adding files to perforce");
		} catch (P4JavaException e) {
			e.printStackTrace();
			throw new IOException("Perforce-problem while adding files to perforce");
		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException("Problem while adding files to perforce");
		}
	}

	/**
	 * Gets a P4Resource.
	 * 
	 * @param source Path to the resource in perforce in p4-depot-notation
	 * @return The resource.
	 * @throws IOException Never thrown, just here to satisfy interface.
	 */
	public Resource getResource(String source) throws IOException {
		Resource resource = (Resource) resourcesCache.get(source);
		if (resource == null) {
			resource = new P4Resource(this, source);
			resourcesCache.put(source, resource);
		}
		return resource;
	}

	/**
	 * Return a listing of resources located at a certain location.
	 * 
	 * @param parent The parent-directory in perforce from which to generate the listing
	 * @return A listing of the parent directory's file content, as a List of Strings.
	 * @throws IOException On listing failure.
	 */
	public List<String> list(String parent) throws IOException {
		if (!authenticated) {
			authenticate();
		}

		try {
			List<String> list = new ArrayList<String>();
			List<IFileSpec> depotFiles = server.getDepotFiles(FileSpecBuilder.makeFileSpecList(parent + "/*"), false);
			List<IFileSpec> depotDirs = server.getDirectories(FileSpecBuilder.makeFileSpecList(parent + "*"), false, false, false);

			// Add files to return list
			if ((depotFiles != null) &&		// Make this bullet-proof since the p4java-api sometimes returns weird results
					(depotFiles.isEmpty() == false) &&  
					(depotFiles.get(0) != null) &&
					(depotFiles.get(0).getAction() != null)) {
				for ( Iterator<IFileSpec> iterator = depotFiles.iterator(); iterator.hasNext(); ) {
					String path = iterator.next().getDepotPathString();
					if (path != null) {
						String[] parts = path.split("/", -1);
						list.add(parts[parts.length - 1]);
					}
				}
			}
			// Add directories to return list
			if ((depotDirs != null) &&
					(depotDirs.isEmpty() == false)) {
				for ( Iterator<IFileSpec> iterator = depotDirs.iterator(); iterator.hasNext(); ) {
					String path = iterator.next().getOriginalPathString();
					if (path != null) {
						String[] parts = path.split("/", -1);
						list.add(parts[parts.length - 1]);
					}
				}
			}
			return list;
		} catch (ConnectionException e) {
			Message.error("Perforce connection problem while listing ressources for " + parent);
			e.printStackTrace();
			throw new IOException("Perforce connection problem");
		} catch (AccessException e) {
			Message.error("Perforce access problem while listing ressources for " + parent);
			e.printStackTrace();
			throw new IOException("Perforce access problem");
		}
	}


	/**
	 * Fetch the needed file information for a given file (size, last modification time) and report it back in a
	 * P4Resource.
	 * 
	 * @param repositorySource Full path to resource in perforce in depot-notation
	 * @return P4Resource filled with the needed informations
	 * @throws IOException If resource can't be resolved due to perforce-access problems
	 */
	protected P4Resource resolveResource(String repositorySource)  throws IOException {
		Message.debug("Resolve resource for " + repositorySource );
		P4Resource result = null;

		if (!authenticated) {
			authenticate();
		}

		List<IExtendedFileSpec> depotFiles = null;
		try {
			depotFiles = server.getExtendedFiles(FileSpecBuilder.makeFileSpecList(repositorySource),null);
		} catch (P4JavaException e) {
			Message.error("Perforce problem while trying to access " + repositorySource);
			e.printStackTrace();
			throw new IOException("Perforce access problem");
		}

		if ((depotFiles != null) && (depotFiles.get(0) != null) && (depotFiles.get(0).getHeadAction() != null)) {
			IExtendedFileSpec f = depotFiles.get(0);
			Message.debug("Resource found at " + repositorySource + ", returning resolved resource");
			// f.getFileSize() returns 0 - don't know why (but it seems we can live with it)
			result = new P4Resource(this, repositorySource, true, f.getHeadTime().getTime(), f.getFileSize());
		} else {
			Message.debug("No resource found at " + repositorySource + ", returning default resource");
			result = new P4Resource();
		} 

		return result;
	}

	/**
	 * Set the perforce server port number
	 * @param p4Port Perforce server port number
	 */
	public void setP4Port(String p4Port) {
		this.p4Port = p4Port;
	}

	/**
	 * Get the perforce port number
	 * @return Perforce port
	 */
	public String getP4Port() {
		return p4Port;
	}

	/**
	 * Set the perforce host name of the repository
	 * @param p4Host Perforce host name
	 */
	public void setP4Host(String p4Host) {
		this.p4Host = p4Host;
	}

	/**
	 * Get the perforce host name of the repository
	 * @return Perforce host name
	 */
	public String getP4Host() {
		return p4Host;
	}

	/**
	 * Set the perforce user with that IvyP4 accesses the repository
	 * @param p4User Perforce user name
	 */
	public void setP4User(String p4User) {
		this.p4User = p4User;
	}

	/**
	 * Get the perforce user with that IvyP4 accesses the repository
	 * @return Perforce user name
	 */
	public String getP4User() {
		return p4User;
	}

	/**
	 * Set the perforce password for this repo
	 * @param p4Passwd The perforce password
	 */
	public void setP4Passwd(String p4Passwd) {
		this.p4Passwd = p4Passwd;
	}

	/**
	 * Return the perforce password for this repo (null, if it has not been given to us)
	 * @return perforce password (may be null)
	 */
	public String getP4Passwd() {
		return p4Passwd;
	}

	/**
	 * Gets the manifest associated with the passed class.
	 * 
	 * @param someClass Class to find manifest for.
	 * @return The manifest.
	 * @throws IOException If the manifest could not be found.
	 */
	private Manifest getManifest(Class<?> someClass) throws IOException {
		String className = someClass.getSimpleName();
		String classFileName = className + ".class";
		String classFilePath = someClass.getName().replace('.', '/') + ".class";
		String pathToThisClass = someClass.getResource(classFileName).toString();
		String pathToManifest2 = pathToThisClass.toString().substring(0, pathToThisClass.length() - classFilePath.length())
		+ "META-INF/MANIFEST.MF";
		InputStream mfs = new URL(pathToManifest2).openStream();
		Manifest manifest = new Manifest(mfs);
		mfs.close();
		return manifest;
	}

	/**
	 * Ensures that no transaction is lingering around.
	 * 
	 * @throws IllegalStateException If a transaction is still active.
	 */
	private void ensureNoPublishTransaction() {
		if (publishTransaction != null) {
			throw new IllegalStateException("Previous transaction is still active");
		}
	}

	/**
	 * Starts a publish transaction.
	 * 
	 * @param mrid The P4 submit message to use for this publish transaction.
	 */
	public void beginPublishTransaction(ModuleRevisionId mrid) {
		ensureNoPublishTransaction();
		Message.debug("Starting transaction " + mrid + " ...");
		this.moduleRevisionId = mrid;
	}

	/**
	 * Revert any leftovers of a started transaction in perforce and on the filesystem
	 */
	public void abortPublishTransaction() {
		P4Utils.deleteClient(server, publishTransaction.getTmpClient());
	}

	/**
	 * Commits the previously started publish transaction.
	 * 
	 * @throws IOException If an error occurs committing the transaction.
	 */
	public void commitPublishTransaction() throws IOException {
		ensurePublishTransaction();
		Message.debug("Committing transaction...");
		try {
			publishTransaction.commit(server);
		} catch (P4JavaException e) {
			Message.error("Perforce problem while committing transaction: " + e.getMessage());
			throw (IOException) new IOException().initCause(e);
		} finally {
			publishTransaction = null;
		}
	}

	/**
	 * Ensure that a transaction was already started.
	 * 
	 * @throws IllegalStateException If no transaction was started.
	 */
	private void ensurePublishTransaction() {
		if (publishTransaction == null) {
			throw new IllegalStateException("Transaction not initialised");
		}
	}

}
