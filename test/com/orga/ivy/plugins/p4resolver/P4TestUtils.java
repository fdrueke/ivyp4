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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.ivy.util.Message;

import com.orga.ivy.plugins.p4resolver.P4Utils;
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

public class P4TestUtils {
	public static void put(IOptionsServer server, File source, String destination, boolean overwrite) throws IOException {
		// create a temp-p4client
		String p4User = server.getUserName();
		String tmpClientName = "ivyp4_" + p4User + "_" + source.getName() + UUID.randomUUID().toString();
		IClient	client = new Client(server);
		client.setName(tmpClientName);
		client.setRoot(source.getParent());
		client.setOwnerName(p4User);
		client.setServer(server);

		ClientView mapping = new ClientView();
		mapping.addEntry(new ClientView.ClientViewMapping(0,destination, "//" + tmpClientName + "/" + source.getName()));
		client.setClientView(mapping);

		try {
			server.createClient(client);
			server.setCurrentClient(client);
		} catch (P4JavaException pexc) {
			Message.error("Error creating perforce-client " + tmpClientName + "\n" + pexc.getMessage());
			throw new IOException("Error creating perforce-client " + tmpClientName + "\n" + pexc.getMessage());
			
		}
		Message.debug("\tcreated tempclient " + tmpClientName);


		// check whether the target already exists in perforce (and is not deleted in head-revision)
		Boolean p4add = true;
		if (P4Utils.p4FileExists(server,destination)) {
			Message.debug("File exists in perforce already: " + destination);

			if (overwrite) {
				Message.debug("updating " + destination);
				p4add = false;	// no add, but edit in perforce
			} else {
				Message.debug("Overwrite set to false, ignoring " + source.getName());
				return;
			}
		}

		Changelist changeListImpl = new Changelist(
				IChangelist.UNKNOWN,
				client.getName(),
				p4User,
				ChangelistStatus.NEW,
				new Date(),
				"submitted by ivyp4resolver",
				false,
				(Server) server
		);

		try {
			IChangelist changelist = client.createChangelist(changeListImpl);

			if (p4add) {
				client.addFiles(
						FileSpecBuilder.makeFileSpecList(destination), false, changelist.getId(), null, false);
			} else {
				// "flush" the file (sync -k)
				client.sync(FileSpecBuilder.makeFileSpecList(destination),false,false,true,false);
				// open for edit
				client.editFiles(
						FileSpecBuilder.makeFileSpecList(destination), false, false, changelist.getId(), null);
				//FileSpecBuilder.makeFileSpecList(destination), null);
			}

			changelist.update();
			changelist.refresh();

			List<IFileSpec> submitFiles = changelist.submit(false);
			if (submitFiles != null) {
				for (IFileSpec fileSpec : submitFiles) {
					if (fileSpec != null) {
						if (fileSpec.getOpStatus() == FileSpecOpStatus.VALID) {
							System.out.println("submitted: " + fileSpec.getDepotPathString());
						} else if (fileSpec.getOpStatus() == FileSpecOpStatus.INFO){
							System.out.println(fileSpec.getStatusMessage());
						} else if (fileSpec.getOpStatus() == FileSpecOpStatus.ERROR){
							System.err.println(fileSpec.getStatusMessage());
						}
					}
				}
			}
		} catch (ConnectionException e) {
			e.printStackTrace();
		} catch (RequestException e) {
			e.printStackTrace();
		} catch (AccessException e) {
			e.printStackTrace();
		} 
	}
	

	/**
	 * Get a file from the perforce-server
	 * 
	 * @param server The perforce server (needs to be initialised and authenticated)
	 * @param source The path to the file in perforce-depot-notation
	 * @param tempFile Where the file will be put 
	 */
	public static void getFile(IOptionsServer server, String source, File tempFile) {
		String[] filePaths = new String[1];
		filePaths[0] = source;

		try {
			List<IFileSpec> fileList = server.getDepotFiles(
					FileSpecBuilder.makeFileSpecList(filePaths), false);

			InputStream p4Content = server.getFileContents(fileList, false, true);
			FileOutputStream destStream = new FileOutputStream( tempFile.getPath() ); 

			byte[] buffer = new byte[ 0xFFFF ]; 
			for ( int len; (len = p4Content.read(buffer)) != -1; ) {
				destStream.write( buffer, 0, len ); 
			}
			destStream.close();
			p4Content.close();

		} catch (P4JavaException e) {
			Message.error("\tcouldn't get file " + source);
			e.printStackTrace();
		} catch ( IOException e ) { 
			Message.error("\tproblem writing to destination");
			e.printStackTrace(); 
		} 
	}
}
