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
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

/**
 * Takes care of permissions
 */
class SlotsDbProxy implements CloseableDataRecorder {

	final SlotsDb master;
	final boolean hasWritePermission;
	final boolean hasAdminPermission;
	private final FendoDbConfiguration config;
	final FendoDbAccessControl accessManager;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	SlotsDbProxy(SlotsDb master, boolean readOnly) {
		this.master = master;
		final SlotsDbFactoryImpl factory = master.getFactory();
		this.accessManager = factory == null ? null : factory.accessManager;
		this.hasWritePermission = !readOnly && !master.getConfiguration().isReadOnlyMode() &&
				(!master.secure || PermissionUtils.mayWrite(master.getPath(), accessManager));
		this.hasAdminPermission = !master.secure || PermissionUtils.hasAdminPermission(master.getPath(), accessManager);
		final FendoDbConfiguration cfg = master.getConfiguration();
		this.config = FendoDbConfigurationBuilder.getInstance(cfg)
				.setReadOnlyMode(readOnly)
				.build();
		master.proxyCount.referenceAdded();
	}

	@Override
	public FendoTimeSeries createRecordedDataStorage(String id, RecordedDataConfiguration configuration)
			throws DataRecorderException {
		if (!hasWritePermission)
			throw new AccessControlException("Write permission required to create a new configuration");
		return SlotsDbStorage.getProxy(master.createRecordedDataStorage(id, configuration), master.proxyCount, false);
	}

	@Override
	public FendoTimeSeries getRecordedDataStorage(String id) {
		return SlotsDbStorage.getProxy(master.getRecordedDataStorage(id), master.proxyCount, !hasWritePermission);
	}

	@Override
	public boolean deleteRecordedDataStorage(String id) {
		if (!hasAdminPermission)
			throw new AccessControlException("Write permission required to delete a configuration");
		return master.deleteRecordedDataStorage(id);
	}

	@Override
	public List<String> getAllRecordedDataStorageIDs() {
		return master.getAllRecordedDataStorageIDs();
	}

	@Override
	public void close() throws IOException {
		if (!closed.getAndSet(true))
			master.proxyCount.referenceRemoved();
	}

	@Override
	protected void finalize() throws Throwable {
		this.close();
	}

	@Override
	public List<FendoTimeSeries> getAllTimeSeries() {
		return master.getAllTimeSeriesInternal()
			.map(ts -> SlotsDbStorage.getProxy(ts, master.proxyCount, !hasWritePermission))
			.collect(Collectors.toList());
	}

	@Override
	public Path getPath() {
		return master.getPath();
	}

	@Override
	public FendoDbConfiguration getConfiguration() {
		return config;
	}

	@Override
	public boolean isActive() {
		return master.isActive();
	}

	@Override
	public DataRecorderReference updateConfiguration(FendoDbConfiguration newConfiguration) throws IOException {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required in order to update the database configuration");
		// will close master
		return master.updateConfiguration(newConfiguration);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration) throws IOException {
		if (master.secure) {
			PermissionUtils.checkPermission(target, FendoDbPermission.ADMIN, accessManager);
			PermissionUtils.checkWritePermission(target, accessManager);
		}
		return master.copy(target, configuration);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration, long startTime, long endTime)
			throws IOException {
		return copy(target, configuration, null, startTime, endTime);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration, TimeSeriesMatcher filter,
			long startTime, long endTime) throws IOException {
		if (master.secure) {
			PermissionUtils.checkPermission(target, FendoDbPermission.ADMIN, accessManager);
			PermissionUtils.checkWritePermission(target, accessManager);
		}
		return master.copy(target, configuration, filter, startTime, endTime);
	}

	@Override
	public boolean isEmpty() {
		return master.isEmpty();
	}

	@Override
	public Map<String, Collection<String>> getAllProperties() {
		return master.getAllProperties();
	}

	@Override
	public Collection<String> getAllPropertyValues(String key) {
		return master.getAllPropertyValues(key);
	}

	@Override
	public Lock getDbLock() {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required for acquiring the database lock");
		return master.getDbLock();
	}

	// equals must be based on == here, because each instance must be closed separately
//	@Override
//	public boolean equals(Object obj) {
//		if (obj == this)
//			return true;
//		if (!(obj instanceof SlotsDbProxy))
//			return false;
//		final SlotsDbProxy other = (SlotsDbProxy) obj;
//		if (!this.master.equals(other.master))
//			return false;
//		if (this.hasWritePermission != other.hasWritePermission)
//			return false;
//		if (this.hasAdminPermission != other.hasAdminPermission)
//			return false;
//		return true;
//	}
//
//	@Override
//	public int hashCode() {
//		return Objects.hash(master, hasWritePermission);
//	}

	@Override
	public String toString() {
		return "FendoDbProxy[" + master + "]";
	}

	@Override
	public List<FendoTimeSeries> findTimeSeries(TimeSeriesMatcher filter) {
		return master.findTimeSeriesInternal(filter)
				.map(ts -> SlotsDbStorage.getProxy(ts, master.proxyCount, !hasWritePermission))
				.collect(Collectors.toList());
	}

	@Override
	public boolean deleteDataBefore(Instant instant) throws IOException {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required to delete data");
		return master.deleteDataBefore(instant);
	}

	@Override
	public boolean deleteDataAfter(Instant instant) throws IOException {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required to delete data");
		return master.deleteDataAfter(instant);
	}

	@Override
	public boolean deleteDataOlderThan(TemporalAmount duration) throws IOException {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required to delete data");
		return master.deleteDataOlderThan(duration);
	}

	// hacky methods for ogema data tagger; called via reflections... do not refactor
	// listener will be informed about newly created time series
	public void registerListener(Consumer<FendoTimeSeries> listener) {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required to register listener");
		master.addTimeSeriesListener(listener);
	}

	public void removeListener(Consumer<FendoTimeSeries> listener) {
		if (!hasAdminPermission)
			throw new AccessControlException("Admin permission required to remove listener");
		master.removeTimeSeriesListener(listener);
	}
	
	@Override
	public void reloadDays() throws IOException {
		master.reloadDays();
	}

}
