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

import java.io.IOException;
import java.io.InputStream;

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.Message;

/**
 * A Resource implementation for Perforce.
 */
public class P4Resource implements Resource {
	
	/**
	 * The path+name of this resource in p4depot-notation as given to us by ivy
	 */
	private String source;
	
	/**
	 * true if this resource was resolved by ivy
	 */
	private boolean exists = false;
	
	/**
	 * when the resource was last modified (committed).
	 */
	private long lastModified = 0;
	
	/**
	 * The size of the resource in bytes
	 */
	private long contentLength = 0;
	
	/**
	 * p4repository object for this resource
	 */
	private P4Repository repository = null;
	private boolean resolved = false;

	/**
	 * Constructs a new Perforce resource.
	 * 
	 * @param repository The repository that was used to resolve this resource.
	 * @param source Perforce string identifying the resource
	 * @param exists Whether the resource exists or not.
	 * @param lastModified When the resource was last modified (committed).
	 * @param contentLength The size of the resource in bytes.
	 */
	public P4Resource(P4Repository repository, String source, boolean exists, long lastModified, long contentLength) {
		this.source = source;
		this.exists = exists;
		this.lastModified = lastModified;
		this.contentLength = contentLength;
		this.resolved = true;
	}

	/**
	 * Constructs a new Perforce resource.
	 * 
	 * @param repository The repository that was used to resolve this resource.
	 * @param source Perforce string identifying the resource
	 */
	public P4Resource(P4Repository repository, String source) {
		this.repository = repository;
		this.source = source;
		this.resolved = false;
	}

	/**
	 * Constructs a new Perforce resource.
	 */
	public P4Resource() {
		this.resolved = true;
	}

	/**
	 * Resolves this resource via its repository.
	 * @throws IOException if resource can't be resolved 
	 */
	private void resolve() throws IOException   {
		P4Resource resolved = repository.resolveResource(source);
		this.contentLength = resolved.getContentLength();
		this.lastModified = resolved.getLastModified();
		this.exists = resolved.exists();
		this.resolved = true;
	}

	/**
	 * Clones this resource.
	 * 
	 * @param cloneName
	 * @return A clone of this resource.
	 */
	public Resource clone(String cloneName) {
		return new P4Resource(repository, cloneName);
	}

	/**
	 * Checks whether this resource is available.
	 * 
	 * @return Whether the resource is available or not.
	 */
	public boolean exists() {
		if (!resolved) {
			try {
				resolve();
			} catch (IOException e) {
				Message.error("Can't find out if " + source + " exists (perforce-access problem)");
				e.printStackTrace();
				return false;
			}
		}
		return this.exists;
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.ivy.repository.Resource#getContentLength()
	 */
	public long getContentLength() {
		if (!resolved) {
			try {
				resolve();
			} catch (IOException e) {
				Message.error("Can't get contentlength of " + source + " (perforce-access problem)");
				e.printStackTrace();
			}
		}
		return this.contentLength;
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.ivy.repository.Resource#getLastModified()
	 */
	public long getLastModified() {
		if (!resolved) {
			try {
				resolve();
			} catch (IOException e) {
				Message.error("Can't get last modificationdate of " + source + " (perforce-access problem)");
				e.printStackTrace();
			}
		}
		return this.lastModified;
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.ivy.repository.Resource#getName()
	 */
	public String getName() {
		return source;
	}

	/**
	 * Returns whether this resource is local or not.
	 * 
	 * @return Currently always returns false.
	 * @see org.apache.ivy.repository.Resource#isLocal()
	 */
	public boolean isLocal() {
		// perforce resources are not on the file system so return false
		return false;
	}

	/**
	 * Gets an input stream for this resource.
	 * 
	 * @return nothing is ever returned
	 * @throw UnsupportedOperationException This is currently always thrown.
	 * @see org.apache.ivy.repository.Resource#openStream()
	 */
	public InputStream openStream() throws IOException {
		throw new UnsupportedOperationException("Opening an input stream on a Perforce resource not currently supported");
	}

	/**
	 * Generates a String representation of this object.
	 * 
	 * @return A String representation of this object.
	 */
	public String toString() {
		return source;
	}

}
