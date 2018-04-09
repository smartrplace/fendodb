package org.smartrplace.logging.fendodb.impl;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.ReductionMode;
import org.ogema.core.timeseries.InterpolationMode;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.FendoTimeSeries;

/**
 * Replaces DynamicProxy approach in SlotsDbStorage,
 * because it is not possible to overwrite the #finalize()
 * method in a Proxy 
 */
class SlotsDbStorageProxy implements FendoTimeSeries {
	
	private final FendoTimeSeries master;
	private final boolean readOnly;
	private final ReferenceCounter counter;
	
	SlotsDbStorageProxy(FendoTimeSeries master, ReferenceCounter counter, boolean readOnly) {
		this.master = master;
		this.readOnly = readOnly;
		this.counter = counter;
		counter.referenceAdded();
	}
	
	@Override
	protected void finalize() throws Throwable {
		counter.referenceRemoved();
	}
	
	@Override
	public void insertValue(SampledValue value) throws DataRecorderException {
		checkWriteAccess();
		master.insertValue(value);
	}

	@Override
	public void insertValues(List<SampledValue> values) throws DataRecorderException {
		checkWriteAccess();
		master.insertValues(values);
	}

	@Override
	public void update(RecordedDataConfiguration configuration) throws DataRecorderException {
		checkWriteAccess();
		master.update(configuration);
	}

	@Override
	public void setConfiguration(RecordedDataConfiguration configuration) {
		checkWriteAccess();
		master.setConfiguration(configuration);
	}

	@Override
	public RecordedDataConfiguration getConfiguration() {
		return master.getConfiguration();
	}

	@Override
	public List<SampledValue> getValues(long startTime, long endTime, long interval, ReductionMode mode) {
		return master.getValues(startTime, endTime, interval, mode);
	}

	@Override
	public String getPath() {
		return master.getPath();
	}

	@Override
	public SampledValue getValue(long time) {
		return master.getValue(time);
	}

	@Override
	public SampledValue getNextValue(long time) {
		return master.getNextValue(time);
	}

	@Override
	public SampledValue getPreviousValue(long time) {
		return master.getPreviousValue(time);
	}

	@Override
	public List<SampledValue> getValues(long startTime) {
		return master.getValues(startTime);
	}

	@Override
	public List<SampledValue> getValues(long startTime, long endTime) {
		return master.getValues(startTime, endTime);
	}

	@Override
	public InterpolationMode getInterpolationMode() {
		return master.getInterpolationMode();
	}

	@Override
	public boolean isEmpty() {
		return master.isEmpty();
	}

	@Override
	public boolean isEmpty(long startTime, long endTime) {
		return master.isEmpty(startTime, endTime);
	}

	@Override
	public int size() {
		return master.size();
	}

	@Override
	public int size(long startTime, long endTime) {
		return master.size(startTime, endTime);
	}

	@Override
	public Iterator<SampledValue> iterator() {
		return master.iterator();
	}

	@Override
	public Iterator<SampledValue> iterator(long startTime, long endTime) {
		return master.iterator(startTime, endTime);
	}

	@Override
	public Long getTimeOfLatestEntry() {
		return master.getTimeOfLatestEntry();
	}

	@Override
	public void setProperty(String key, String value) {
		checkWriteAccess();
		master.setProperty(key, value);
	}

	@Override
	public void addProperty(String key, String value) {
		checkWriteAccess();
		master.addProperty(key, value);
	}

	@Override
	public void addProperties(String key, Collection<String> properties) {
		checkWriteAccess();
		master.addProperties(key, properties);
	}

	@Override
	public void setProperties(Map<String, Collection<String>> properties) {
		checkWriteAccess();
		master.setProperties(properties);
	}

	@Override
	public void addProperties(Map<String, Collection<String>> properties) {
		checkWriteAccess();
		master.addProperties(properties);
	}

	@Override
	public boolean removeProperty(String key) {
		checkWriteAccess();
		return master.removeProperty(key);
	}

	@Override
	public boolean removeProperty(String key, String value) {
		checkWriteAccess();
		return master.removeProperty(key, value);
	}

	@Override
	public Map<String, List<String>> getProperties() {
		return master.getProperties();
	}

	@Override
	public String getFirstProperty(String key) {
		return master.getFirstProperty(key);
	}

	@Override
	public List<String> getProperties(String key) {
		return master.getProperties(key);
	}

	@Override
	public boolean hasProperty(String key) {
		return master.hasProperty(key);
	}

	@Override
	public boolean hasProperty(String tag, boolean regexpMatching) {
		return master.hasProperty(tag, regexpMatching);
	}
	
	private final void checkWriteAccess() {
		if (readOnly)
    		throw new AccessControlException("Method call not allowed in read-only mode");
	}

	@Override
	public String toString() {
		return "SlotsDbStorageProxy[" + master.toString() + "]";
	}
	
}
 