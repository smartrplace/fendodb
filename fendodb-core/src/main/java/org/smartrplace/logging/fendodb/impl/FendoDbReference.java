/**
 * ﻿Copyright 2018 Smartrplace UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartrplace.logging.fendodb.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.util.Objects;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;

class FendoDbReference implements DataRecorderReference {

	private volatile SlotsDb master;
	private final boolean isSecure;
	private final Path path;
	private final FendoDbConfiguration config;
	private final FendoDbFactory factory;

	FendoDbReference(SlotsDb master, boolean isSecure) {
		this.master = Objects.requireNonNull(master);
		this.isSecure = isSecure;
		master.proxyCount.referenceAdded();
		this.path = master.getPath();
		this.config = master.getConfiguration();
		this.factory = master.getFactory();
	}

	FendoDbReference(Path path, FendoDbConfiguration config, FendoDbFactory factory, boolean isSecure) {
		this.master = null;
		this.isSecure = isSecure;
		this.path = path;
		this.config = config;
		this.factory = factory;
	}

	@Override
	protected void finalize() throws Throwable {
		if (master != null)
			master.proxyCount.referenceRemoved();
	}

	@Override
	public Path getPath() {
		return path;
	}

	/**
	 * May be null!
	 */
	@Override
	public FendoDbConfiguration getConfiguration() {
		return config;
	}

	@Override
	public CloseableDataRecorder getDataRecorder() throws IOException {
		return getDataRecorder(null);
	}

	@Override
	public CloseableDataRecorder getDataRecorder(final FendoDbConfiguration configuration) throws IOException {
		boolean cfgReadOnly = (configuration != null && configuration.isReadOnlyMode())
				|| (configuration == null && this.config.isReadOnlyMode());
		if (!cfgReadOnly && this.isSecure) {
			final FendoDbAccessControl accessControl = factory == null ? null : ((SlotsDbFactoryImpl) factory).accessManager;
			cfgReadOnly = !PermissionUtils.mayWrite(path, accessControl);
			if (cfgReadOnly && configuration != null && !configuration.isReadOnlyMode())
				throw new AccessControlException("Write access to database not permitted");
		}
		final SlotsDb master = checkState();
		// may be null if database configuration is currently being updated; but this should not happen
		// during normal operation
		return master == null ? null : master.getProxyDb(cfgReadOnly);
	}

	SlotsDb getMaster() {
		return master;
	}

	@Override
	public String toString() {
		return "FendoDbReference for [" + (master != null ? master.toString() : path) + "]";
	}

	private SlotsDb checkState() throws IOException {
		final SlotsDb initial = this.master;
		if (initial != null && initial.isActive())
			return initial;
		synchronized (this) {
			if (master == null || !master.isActive()) {
				master = ((SlotsDbFactoryImpl) factory).getExistingInstanceInternal(path);
				if (master != null) 
					master.proxyCount.referenceAdded();
			}
			return master;
		}
	}

}
