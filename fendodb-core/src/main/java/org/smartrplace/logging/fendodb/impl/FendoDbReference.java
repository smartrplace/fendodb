/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.util.Objects;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;

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
			cfgReadOnly = !PermissionUtils.mayWrite(master.getPath());
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
