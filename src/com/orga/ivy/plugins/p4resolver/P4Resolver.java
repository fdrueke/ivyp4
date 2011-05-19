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

import java.io.IOException;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

/**
 * An Ivy resolver for Perforce repositories.
 */
public class P4Resolver extends RepositoryResolver {

	/**
	 * Registers a new resolver for p4 patterns.
	 */
	public P4Resolver() {
		setRepository(new P4Repository());
	}

	/**
	 * Gets the Repository in use by this resolver, casting it to the correct type.
	 * 
	 * @return The repository in use by this resolver.
	 */
	protected P4Repository getP4Repository() {
		return (P4Repository) getRepository();
	}

	@Override
	public void beginPublishTransaction(ModuleRevisionId mrid, boolean flag) throws IOException {
		getP4Repository().beginPublishTransaction(mrid);
	}

	@Override
	public void abortPublishTransaction() throws IOException {
		getP4Repository().abortPublishTransaction();
	}

	@Override
	public void commitPublishTransaction() throws IOException {
		getP4Repository().commitPublishTransaction();
	}

	/**
	 * Determines whether a parameter is valid or not. Parameters that are determined to be "unset" property placeholders
	 * will be silently ignored.
	 * 
	 * @param parameter Parameter to check.
	 * @return True if the parameter is valid, false otherwise.
	 */
	private boolean validParameter(String parameter) {
		if (parameter != null) {
			if (!parameter.startsWith("${")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Set the port number for the P4 server.
	 * 
	 * @param portNumber
	 */
	public void setPort(String port) {
		if (validParameter(port)) {
			getP4Repository().setP4Port(port.trim());
		}else {
			// optional parameter - in case it's set to an unresolved property we simply set it to null
			getP4Repository().setP4Port(null);
		}
	}

	/**
	 * Set the host number for the P4 server.
	 * 
	 * @param host
	 */
	public void setHost(String host) {
		if (validParameter(host)) {
			getP4Repository().setP4Host(host.trim());
		} else {
			// optional parameter - in case it's set to an unresolved property we simply set it to null
			getP4Repository().setP4Host(null);
		}
	}

	/**
	 * Set the user name to use to connect to the p4 repository.
	 * 
	 * @param userName The p4 username.
	 */
	public void setUser(String user) {
		if (validParameter(user)) {
			getP4Repository().setP4User(user.trim());
		} else {
			// optional parameter - in case it's set to an unresolved property we simply set it to null
			getP4Repository().setP4User(null);
		}
	}

	/**
	 * Set the password to use to connect to the p4 repository.
	 * 
	 * @param userPassword The p4 password.
	 */
	public void setPasswd(String passwd) {
		if (validParameter(passwd)) {
			getP4Repository().setP4Passwd(passwd);
		} else {
			// optional parameter - in case it's set to an unresolved property we simply set it to null
			getP4Repository().setP4Passwd(null);
		}
	}
}
