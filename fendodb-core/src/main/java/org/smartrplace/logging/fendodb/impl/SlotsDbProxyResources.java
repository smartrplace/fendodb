package org.smartrplace.logging.fendodb.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.ogema.accesscontrol.ResourcePermission;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.persistence.ResourceDB;
import org.ogema.recordeddata.DataRecorderException;
import org.ogema.resourcetree.TreeElement;
import org.osgi.service.component.ComponentServiceObjects;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

/**
 * A version of the SlotsDbProxy that also checks 
 * ResourcePermissions for the timeseries it grants access to.
 * Relevant if the FendoDb instance is used as OGEMA database for historical data.
 */
// TODO so far only read permissions are checked, what about write permissions?
public class SlotsDbProxyResources implements CloseableDataRecorder {
	
	// actually of type SlotsdbProxy, but this should not be relevant
	private final CloseableDataRecorder master;
	private final FendoDbAccessControl accessManager;
	private final ComponentServiceObjects<ResourceDB> resourceDbService;
	private final boolean hasAdminPermission;

	public SlotsDbProxyResources(SlotsDbProxy master, ComponentServiceObjects<ResourceDB> resourceDbService) {
		this.master = master;
		this.accessManager = master.accessManager;
		this.hasAdminPermission = master.hasAdminPermission;
		this.resourceDbService = resourceDbService;
	}
	
	private boolean mayAccess(String path, boolean readOrWrite) {
		if (this.accessManager == null || this.hasAdminPermission)
			return true;
		if (path.startsWith("/"))
			path = path.substring(1);
		final TreeElement te = this.getTreeElement(path);
		if (te == null) 
			return true;// FIXME?
		final ResourcePermission perm = new ResourcePermission(readOrWrite ? ResourcePermission.READ : ResourcePermission.WRITE, te, 0);
		final AccessControlContext ctx = this.accessManager.getAccessControlContext();
		try {
			if (ctx != null)
				ctx.checkPermission(perm) ;
			else {
				final SecurityManager sman = System.getSecurityManager();
				if (sman != null)
					sman.checkPermission(perm);
			}
			return true;
		} catch (SecurityException e) {
			return false;
		}
		
	}
	
	
	private TreeElement getTreeElement(final String path) {
		final String[] components = path.split("/");
		final ResourceDB resDb = this.resourceDbService.getService();
		try {
			TreeElement te = resDb.getToplevelResource(components[0]);
			for (int i=1; i<components.length; i++) {
				if (te == null)
					break;
				te = te.getChild(components[i]);
			}
			return te;
		} finally {
			this.resourceDbService.ungetService(resDb);
		}
	}
	
	//------------------------------------------------
	// the following methods require special timeseries-specific permissions for OGEMA historic resource data
	//------------------------------------------------

	@Override
	public List<String> getAllRecordedDataStorageIDs() {
		return master.getAllRecordedDataStorageIDs().stream()
				.filter(id -> this.mayAccess(id, true))
				.collect(Collectors.toList());
	}

	@Override
	public FendoTimeSeries getRecordedDataStorage(String id) {
		if (!this.mayAccess(id, true))
			throw new AccessControlException("Missing read permission for timeseries " + id);
		return master.getRecordedDataStorage(id);
	}

	@Override
	public FendoTimeSeries createRecordedDataStorage(String id, RecordedDataConfiguration configuration)
			throws DataRecorderException {
		if (!this.mayAccess(id, true))
			throw new AccessControlException("Missing read permission for timeseries " + id);
		return master.createRecordedDataStorage(id, configuration);
	}

	@Override
	public List<FendoTimeSeries> findTimeSeries(TimeSeriesMatcher filter) {
		return master.findTimeSeries(filter).stream()
				.filter(ts -> this.mayAccess(ts.getPath(), true))
				.collect(Collectors.toList());
	}

	@Override
	public Collection<String> getAllPropertyValues(String key) {
		return master.getAllPropertyValues(key);
	}

	@Override
	public Map<String, Collection<String>> getAllProperties() {
		return master.getAllProperties();
	}
	
	//--------------------------------------------------------
	// methods below this point need no special treatment in terms of permissions (either require write or admin permission or no 
	// timeseries-specific perm at all)
	//---------------------------------------------------------


	@Override
	public boolean deleteRecordedDataStorage(String id) {
		return master.deleteRecordedDataStorage(id);
	}

	@Override
	public void close() throws IOException {
		master.close();
	}

	@Override
	public List<FendoTimeSeries> getAllTimeSeries() {
		return master.getAllTimeSeries();
	}


	@Override
	public boolean deleteDataBefore(Instant instant) throws IOException {
		return master.deleteDataBefore(instant);
	}

	@Override
	public boolean deleteDataAfter(Instant instant) throws IOException {
		return master.deleteDataAfter(instant);
	}

	@Override
	public boolean deleteDataOlderThan(TemporalAmount amount) throws IOException {
		return master.deleteDataOlderThan(amount);
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
	public boolean isActive() {
		return master.isActive();
	}

	@Override
	public boolean isEmpty() {
		return master.isEmpty();
	}

	@Override
	public DataRecorderReference updateConfiguration(FendoDbConfiguration newConfiguration) throws IOException {
		return master.updateConfiguration(newConfiguration);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration) throws IOException {
		return master.copy(target, configuration);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration, long startTime, long endTime)
			throws IOException {
		return master.copy(target, configuration, startTime, endTime);
	}

	@Override
	public DataRecorderReference copy(Path target, FendoDbConfiguration configuration, TimeSeriesMatcher filter,
			long startTime, long endTime) throws IOException {
		return master.copy(target, configuration, filter, startTime, endTime);
	}

	@Override
	public Lock getDbLock() {
		return master.getDbLock();
	}

	@Override
	public void reloadDays() throws IOException {
		master.reloadDays();
	}

	// hacky methods for ogema data tagger; called via reflections... do not refactor
	// listener will be informed about newly created time series
	public void registerListener(Consumer<FendoTimeSeries> listener) {
		((SlotsDbProxy) master).registerListener(listener);
	}

	public void removeListener(Consumer<FendoTimeSeries> listener) {
		((SlotsDbProxy) master).removeListener(listener);
	}
	
	@Override
	public String toString() {
		return "FendoDbProxyResource[" + master + "]";
	}
	
	
}
