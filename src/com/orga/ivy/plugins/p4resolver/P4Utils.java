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
import java.util.List;

import org.apache.ivy.util.Message;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.IServerInfo;

/**
 * Perforce related helper methods (authentication, file/folder existence checks)
 *   
 * @author Felix Drueke
 */
public class P4Utils {

	/**
	 * Login in to perforce 
	 * 
	 * @param server server object which will be used for authentication
	 * @param host hostname of perforce-server
	 * @param port portnumber of perforce-server
	 * @param user username to use for authentication
	 * @param passwd password to use for authentication
	 * @return true if authentication succeeded, otherwise false
	 * @throws Exception if user is not set and cannot be taken from env/ if passwd is needed but not set 
	 */
	public static boolean authenticate(IOptionsServer server, String serverUriString, String user , String passwd) throws Exception {
		
		// Find out who we are
		if (user == null) {
			Message.debug("Perforce user not set via properties - trying environment-variable");

			user = System.getenv("P4USER");	

			if (user == null) {
				Message.error("Perforce user could not be determined!");
				throw new Exception("Can't determine your perforce username");
			} else {
				Message.debug("Found out that you're " + user + " via the environment-variable P4USER");
			}
		} else {
			Message.debug("Found out that you're " + user + " via properties");
		}

		// setup server connection
		try {
			server.connect();
			server.setUserName(user);
			IServerInfo info = server.getServerInfo();
			if (info != null) {
				Message.debug(
						"Info from Perforce server at URI '"
						+ serverUriString + "' for '"
						+ server.getUsageOptions().getProgramName() + "':");
				Message.debug(formatInfo(info));
			} 

			// check if we're already authenticated via a ticket
			String loginStatus = server.getLoginStatus();
			if (!loginStatus.contains(" ticket expires in ")) {
				// not authenticated via ticket - try it with the (hopefully) provided password
				Message.info("Not authenticated via p4-ticket - trying with password");

				if (passwd == null) {
					Message.debug("No password set via properties - trying environment-variable");
					passwd = System.getenv("P4PASSWD");	
					if (passwd == null) {
						Message.error("Perforce password could not be determined!");
						throw new Exception("Can't determine your perforce password");
					} else {
						Message.debug("Using your perforce-password set by environment-variable P4PASSWD");
					}
				} else {
					Message.debug("Using your perforce-password set by properties");
				}
				server.login(passwd);
			} else {
				Message.debug("Already authenticated via p4-ticket");
			}
		} catch (RequestException rexc) {
			System.err.println(rexc.getDisplayString());
			rexc.printStackTrace();
			return false;
		} catch (P4JavaException exc) {
			System.err.println(exc.getLocalizedMessage());
			exc.printStackTrace();
			return false;
		} 

		return true;
	}




	/**
	 * Check whether a folder exists in perforce
	 * (requires an initialised server) 
	 * 
	 * @param f Folder (p4-depotpath) to be checked
	 * @return true if folder exists, otherwise false.
	 */
	public static boolean p4FolderExists(IOptionsServer server, String f) {

		try {
			List<IFileSpec> depotDirs = server.getDirectories(FileSpecBuilder.makeFileSpecList(f + "*"),false, false, false);

			if (depotDirs.isEmpty()) {
				return false;
			}
		} catch (ConnectionException e) {
			Message.error("Can't connect perforce-server");
			e.printStackTrace();
			return false;
		} catch (AccessException e) {
			Message.error("Can't access perforce-server");
			e.printStackTrace();
			return false;
		}

		return true;
	}


	/**
	 * formats the p4-serverinfo
	 * 
	 * @param info Serverinfo
	 * @return formatted string
	 */
	private static String formatInfo(IServerInfo info) {
		return "\tserver address: " + info.getServerAddress() + "\n"
		+ "\tserver version" + info.getServerVersion() + "\n"
		+ "\tclient address: " + info.getClientAddress() + "\n"
		+ "\tclient working directory: " + info.getClientCurrentDirectory() + "\n"
		+ "\tclient name: " + info.getClientName() + "\n"
		+ "\tuser name: " + info.getUserName();
	}


	/**
	 * Check whether a file exists in perforce and is NOT deleted in the head-revision
	 * (requires an initialised server) 
	 * 
	 * @param f File (p4-depotpath) to be checked
	 * @return true if file exists and is not deleted, otherwise false.
	 */
	public static boolean p4FileExists(IOptionsServer server, String f) {

		try {
			List<IFileSpec> depotFiles = server.getDepotFiles(FileSpecBuilder.makeFileSpecList(f),false);
			if (depotFiles.isEmpty() ||
					(depotFiles.get(0) == null) ||
					(depotFiles.get(0).getAction() == null) || 
					(depotFiles.get(0).getAction() == FileAction.DELETE)) {
				return false;
			}
		} catch (ConnectionException e) {
			Message.error("Can't connect perforce-server");
			e.printStackTrace();
			return false;
		} catch (AccessException e) {
			Message.error("Can't access perforce-server");
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
	
	
	/**
	 * Deletes a directory-tree
	 * 
	 * @param dir directory
	 * @return true if deletion was successful
	 */
	public static boolean deleteDir(File dir) {
		Message.debug("Deleting temporary directory " + dir.getPath());
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i=0; i<children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					Message.warn("Couldn't remove dir " + dir.getAbsolutePath());
					Message.warn("Please cleanup yourself.");
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	/**
	 * Delete a perforce client.
	 * Any open files will be reverted.
	 * Any pending changelists will be deleted.
	 * 
	 * @param server
	 * @param client
	 */
	public static void deleteClient(IOptionsServer server, IClient client) {
		Message.debug("Deleting temporary perforce client " + client.getName());
		
		// revert open files if any 
		try {
			client.revertFiles(FileSpecBuilder.makeFileSpecList("//..."), false, 0, false, true);
		} catch (ConnectionException e) {
			Message.warn("Perforce connection problem while reverting files of client " + client.getName());
			Message.warn("Please cleanup yourself.");
			e.printStackTrace();
		} catch (AccessException e) {
			Message.warn("Perforce access problem while reverting files of client " + client.getName());
			Message.warn("Please cleanup yourself.");
			e.printStackTrace();
		}
		
		// Remove any pending changes. 
		// This can happen if nothing was submitted because
		// the target-files exist already in perforce and "overwrite" is set to "false".
		try {
			List<IChangelistSummary> pending = server.getChangelists(1000,  // restrict to the last 1000 changes
							null,  											// don't restrict to any path 
							client.getName(), 								// restrict to changes for our client 
							client.getOwnerName(),  						// restrict to owner of client 
							false,  										// includeIntegrated = false
							false, 											// longdescs = false
							false, 											// don't restrict to submitted changelists 
							true  											// restrict to pending changelists
							);
			if (pending != null) {
				for (IChangelistSummary c : pending) {
					if (c != null) {
						if (c.getStatus() == ChangelistStatus.PENDING) {
							server.deletePendingChangelist(c.getId());
							Message.debug("Deleted pending changelist " + c.getId());
						} else {
							Message.warn("Something impossible happened while deleting pending changelists. " + 
									"(change " + c.getId() + ", status: " + c.getStatus() +")");
						}
					}
				}
			} else {
				Message.debug("No pending changelists");
			}
		} catch (ConnectionException e) {
			Message.error("Error while deleting pending changes");
			e.printStackTrace();
		} catch (RequestException e) {
			Message.error("Error while deleting pending changes");
			e.printStackTrace();
		} catch (AccessException e) {
			Message.error("Error while deleting pending changes");
			e.printStackTrace();
		}
		 
		// delete client
		try {
			server.deleteClient(client.getName(),false);
			Message.debug("Deleted client " + client.getName());
		} catch (ConnectionException e) {
			Message.error("Error deleting client " + client.getName());
			e.printStackTrace();
		} catch (RequestException e) {
			Message.error("Error deleting client " + client.getName());
			e.printStackTrace();
		} catch (AccessException e) {
			Message.error("Error deleting client " + client.getName());
			e.printStackTrace();
		}
	}
}
