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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.util.Message;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IOptionsServer;

/**
 * Class that encapsulates actions required to perform a number of Ivy put requests as a single Perforce submit. 
 *  
 * @author Felix Drueke
 */
public class P4PublishTransaction {

	/**
	 *  The temporary p4-client for adding/editing files
	 */
	private IClient tmpClient;

	/**
	 *  The perforce-changelist that represents the transaction
	 */
	private IChangelist changelist;

	/**
	 * buffersize for copiing files
	 */
	private static final int copyFileBuffer = 1024;

	/**
	 * name of system-tmpdir-property
	 */
	private static final String tmpDirProp = "java.io.tmpdir";

	/**
	 * system-tmpdir
	 */
	private static final String systemTemp = System.getProperty(tmpDirProp);

	/**
	 * Prefix for temporary perforce client names
	 */
	private static final String clPrefix = "ivyp4_";

	/**
	 * yes - a slash
	 */
	private static final String slash = "/";

	/**
	 * two slashes actually
	 */
	private static final String doubleSlash = "//";

	/**
	 * a slash followed by 3 dots
	 */
	private static final String slashDots = "/...";


	/**
	 * Start a publish-transaction by creating a perforce-client
	 * 
	 * @param server initialised p4-server-object (must be authenticated already)
	 * @param destination target location on the perforce-server in perforce depot notation 
	 * @param mrid ModuleRevisionId 
	 * @throws Exception In case of all sorts of problems with perforce (connection, access, request)
	 */
	public P4PublishTransaction(IOptionsServer server, String destination, ModuleRevisionId mrid) throws Exception {

		// create a temp-p4client
		String p4User = server.getUserName();
		if (p4User == null) {
			throw new Exception("Perforce user undefined");
		}
		String tmpClientName = clPrefix + p4User + UUID.randomUUID().toString();
		tmpClient = new Client(server);
		tmpClient.setName(tmpClientName);
		tmpClient.setRoot(systemTemp + slash + tmpClientName);
		try {
			new File(tmpClient.getRoot()).mkdir();
		} catch (NullPointerException e) {
			e.printStackTrace();
			throw new Exception("Unable to create root-dir for temporary perforce client: " + tmpClient.getRoot());
		}
		tmpClient.setOwnerName(p4User);
		tmpClient.setServer(server);
		ClientView mapping = new ClientView();
		String viewLeftSide = destination.substring(0, destination.indexOf(slash, 3)) + slashDots; // we want to map the depotname, nothing more
		String viewRightSide = doubleSlash + tmpClientName + slashDots;
		mapping.addEntry(new ClientView.ClientViewMapping(0,viewLeftSide, viewRightSide));
		tmpClient.setClientView(mapping);

		try {
			server.createClient(tmpClient);
			server.setCurrentClient(tmpClient);
		} catch (P4JavaException pexc) {
			Message.error("Error creating perforce-client " + tmpClientName);
			Message.error("Perforce error message: " + pexc.getMessage());
			throw pexc;
		}
		Message.debug("created tempclient " + tmpClientName);

		// create Changelist
		Changelist changeListImpl = new Changelist(
				IChangelist.UNKNOWN,	// changelist-id yet unknown
				tmpClient.getName(),	// clientname
				p4User,					// username
				ChangelistStatus.NEW,	// new changelist
				new Date(),				// current date
				"Ivy publishing " + mrid.getOrganisation() + "#" + mrid.getName() + ";" + mrid.getRevision(), // submission text
				false,					// don't shelve this
				(Server) server			// the p4 server
		);

		changelist = tmpClient.createChangelist(changeListImpl);
	}

	/**
	 * Open a file for 'add' or 'edit' in perforce
	 * 
	 * @param server Initialised p4-server-object (must be authenticated already)
	 * @param source The local file that is to be added (relative or absolute)
	 * @param destination Target location on the perforce-server
	 * @param overwrite Whether or not to overwrite the file in perforce if it exists already 
	 * @throws IOException
	 * @throws ConnectionException
	 * @throws AccessException
	 * @throws RequestException
	 */
	public void addPutOperation(IOptionsServer server, File source, String destination, boolean overwrite) throws IOException, ConnectionException, AccessException, RequestException {

		// check whether the target already exists in perforce (and is not deleted in head-revision)
		boolean p4add = true;
		if (P4Utils.p4FileExists(server,destination)) {
			Message.debug("File exists in perforce already: " + destination);

			if (overwrite) {
				Message.debug("updating " + destination);
				p4add = false;	// no add, but edit in perforce
			} else {
				Message.info("Overwrite set to false, ignoring " + source.getName());
				return;
			}
		}

		// copy source to client-tempdir
		String destName = tmpClient.getRoot() + slash + destination.substring(destination.indexOf(slash, 3)+1);
		copyFile(source,new File(destName));

		if (p4add) {
			tmpClient.addFiles(
					FileSpecBuilder.makeFileSpecList(destination), false, changelist.getId(), "binary", false);
		} else {
			// "flush" the file (sync -k)
			tmpClient.sync(FileSpecBuilder.makeFileSpecList(destination), false, false, true, false);
			// open for edit
			tmpClient.editFiles(
					FileSpecBuilder.makeFileSpecList(destination), false, false, changelist.getId(), null);
		}
	}

	/**
	 * Commits all files scheduled to be published
	 * 
	 * @throws P4JavaException If an error occurs committing the transaction.
	 * @throws IOException If an error occurs reading any file data.
	 */
	public void commit(IOptionsServer server) throws P4JavaException, IOException {

		changelist.update();
		changelist.refresh();

		if (changelist.getFiles(false).size() != 0) { // only submit if there are open files actually
			List<IFileSpec> submitFiles = changelist.submit(false);
			if (submitFiles != null) {
				for (IFileSpec fileSpec : submitFiles) {
					if (fileSpec != null) {
						if (fileSpec.getOpStatus() == FileSpecOpStatus.VALID) {
							Message.info("submitted: " + fileSpec.getDepotPathString());
						} else if (fileSpec.getOpStatus() == FileSpecOpStatus.INFO){
							Message.info("submitted: " + fileSpec.getDepotPathString() + "(" + fileSpec.getStatusMessage() + ")");
						} else if (fileSpec.getOpStatus() == FileSpecOpStatus.ERROR){
							Message.error("Error submitting files");
							throw new P4JavaException("Can't submit file! (" + fileSpec.getStatusMessage() +")");
						}
					}
				}
			}
		} else {
			Message.info("Nothing to submit!");
		}

		// cleanup (remove client and temp-dir)
		P4Utils.deleteDir(new File(tmpClient.getRoot()));  
		P4Utils.deleteClient(server,tmpClient);
	}



	/**
	 * Copy a file locally (on filesystem)
	 * 
	 * @param src sourcefile
	 * @param dst targetfile
	 * @throws IOException
	 */
	private static void copyFile(File src, File dst) throws IOException {
		InputStream in = null;
		OutputStream out = null;

		if (dst.getParent() == null) {
			throw new IOException("Can't copy file " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
		}

		if (!new File(dst.getParent()).isDirectory()) {
			// create directory if it doesn't exist yet
			if (new File(dst.getParent()).mkdirs() == false) {
				Message.error("Can't copy file - error creating dir " + dst.getParent());
				throw new IOException("Can't copy file - error creating dir " + dst.getParent());
			}
		}
		try {
			in = new FileInputStream(src);
			out = new FileOutputStream(dst);

			// Transfer bytes from in to out
			byte[] buf = new byte[copyFileBuffer];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
		}catch(final IOException e){
			Message.error("Error copiing file " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
			throw new IOException("Error copiing file " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
		} finally {
			if (in != null) { in.close(); }
			if (out != null) { out.close(); }
		}
	}

	/**
	 * Get the temporary perforce client for this transaction
	 * 
	 * @return perforce client (maybe null) 
	 */
	public IClient getTmpClient() {
		return tmpClient;
	}
}
