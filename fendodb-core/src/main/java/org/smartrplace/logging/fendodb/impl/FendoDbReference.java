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
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;

// FIXME we should not need to create the instance just to get a reference!
class FendoDbReference implements DataRecorderReference {

	private volatile SlotsDb master;
	private final boolean isSecure;

	FendoDbReference(SlotsDb master, boolean isSecure) {
		this.master = Objects.requireNonNull(master);
		this.isSecure = isSecure;
		master.proxyCount.referenceAdded();
	}

	@Override
	protected void finalize() throws Throwable {
		master.proxyCount.referenceRemoved();
	}

	@Override
	public Path getPath() {
		return master.getPath();
	}

	@Override
	public FendoDbConfiguration getConfiguration() {
		return master.getConfiguration();
	}

	@Override
	public CloseableDataRecorder getDataRecorder() throws IOException {
		return getDataRecorder(null);
	}

	@Override
	public CloseableDataRecorder getDataRecorder(final FendoDbConfiguration configuration) throws IOException {
		boolean cfgReadOnly = (configuration != null && configuration.isReadOnlyMode())
				|| (configuration == null && master.getConfiguration().isReadOnlyMode());
		if (!cfgReadOnly && this.isSecure) {
			final SlotsDbFactoryImpl factory = master.getFactory();
			final FendoDbAccessControl accessControl = factory == null ? null : factory.accessManager;
			cfgReadOnly = !PermissionUtils.mayWrite(master.getPath(), accessControl);
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
		return "FendoDbReference for [" + master.toString() + "]";
	}

	private SlotsDb checkState() throws IOException {
		final SlotsDb initial = this.master;
		if (initial != null && initial.isActive())
			return initial;
		synchronized (this) {
			if (master == null || !master.isActive()) {
				master = master.getFactory().getExistingInstanceInternal(master.getPath());
				if (master != null) 
					master.proxyCount.referenceAdded();
			}
			return master;
		}
	}

}
