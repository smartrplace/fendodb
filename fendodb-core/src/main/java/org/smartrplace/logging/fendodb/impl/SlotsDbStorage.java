/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.ogema.core.channelmanager.measurements.IllegalConversionException;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.core.recordeddata.ReductionMode;
import org.ogema.core.timeseries.InterpolationMode;
import org.ogema.recordeddata.DataRecorderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.impl.reduction.Reduction;
import org.smartrplace.logging.fendodb.impl.reduction.ReductionFactory;

class SlotsDbStorage implements FendoTimeSeries {

	/*
	private static final Map<String, Method> readOnlyMethods;
	private static final Map<String, Method> writeMethods;
	*/

	// guarded by lock
	private RecordedDataConfiguration configuration;
	private final String id;
	private final String idEncoded;
	private final SlotsDb recorder;
	// write operations are synchronized on tags itself
	final Map<String, List<String>> tags = new ConcurrentHashMap<>(4);
	
	/*
	static {
		readOnlyMethods = AccessController.doPrivileged(new PrivilegedAction<Map<String,Method>>() {

			@Override
			public Map<String, Method> run() {
				return Stream.concat(
						Arrays.stream(FendoTimeSeries.class.getDeclaredMethods())
							.filter(m -> !isWriteMethod(m)),
						Stream.concat(Arrays.stream(RecordedData.class.getMethods()),
								      Arrays.stream(Object.class.getMethods())))
					.collect(Collectors.toMap(m -> m.getName() + m.getParameterCount(), Function.identity()));
			}
		});
		writeMethods = AccessController.doPrivileged(new PrivilegedAction<Map<String,Method>>() {

			@Override
			public Map<String, Method> run() {
				return Stream.concat( 
						Arrays.stream(FendoTimeSeries.class.getDeclaredMethods())
							.filter(m -> isWriteMethod(m)), // FIXME also contains read methods!
						Arrays.stream(RecordedDataStorage.class.getDeclaredMethods()))
					.collect(Collectors.toMap(m -> m.getName() + m.getParameterCount(), Function.identity()));
			}
		});
	}
	
	
	private static boolean isWriteMethod(final Method m) {
		final String name = m.getName();
		return name.startsWith("set") || name.startsWith("remove") || name.startsWith("add");
	}
	*/
	
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private final static Logger logger = LoggerFactory.getLogger(SlotsDbStorage.class);

	SlotsDbStorage(String id, RecordedDataConfiguration configuration, SlotsDb recorder) {
		this.configuration = configuration;
		this.id = id;
		this.recorder = recorder;
		this.idEncoded = encodedLabel(id);
	}
	
	static FendoTimeSeries getProxy(final FendoTimeSeries storage, final ReferenceCounter counter, final boolean readOnly) {
		if (storage == null)
			return null;
		return new SlotsDbStorageProxy(storage, counter, readOnly);
		/*return (FendoTimeSeries) Proxy.newProxyInstance(SlotsDbStorage.class.getClassLoader(), new Class[] { FendoTimeSeries.class }, 
				(proxy, method, methodArgs) -> {
					final String id = method.getName() + method.getParameterCount();
				    final Method m = readOnlyMethods.get(id);
				    if (m != null)
					    return m.invoke(storage, methodArgs);
				    final Method write = writeMethods.get(id);
				    if (write != null) {
				    	if (readOnly)
				    		throw new AccessControlException(method.getName() + " not accessible in read-only mode");
				    	return write.invoke(storage, methodArgs);
				    }
				    throw new NoSuchMethodException("Method " + method.getName() + " not found");
			});
			*/
	}
	
	private final static String encodedLabel(final String label) {
		// encoding should be compatible with usual linux & windows file system file names
		String encodedLabel;
		try {
			encodedLabel = URLEncoder.encode(label, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		// should be false in any reasonable system
		boolean use252F = Boolean.getBoolean("org.ogema.recordeddata.slotsdb.use252F");
		if (use252F) {
			encodedLabel = encodedLabel.replace("%2F", "%252F");				
		} else {
			encodedLabel = encodedLabel.replace("%252F", "%2F");				
		}
		return encodedLabel;
	}
	
	@Override
	public String getPath() {
		return id;
	}

	@Override
	public void insertValue(final SampledValue value) throws DataRecorderException {
		// FIXME or use read-only proxy instead?
		if (recorder.getProxy().isReadOnlyMode())
			throw new AccessControlException("Database has been opened in read-only mode");
		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

				@Override
				public Void run() throws Exception {
					lock.writeLock().lock();
					try {
						if (configuration != null) {
							recorder.getProxy().appendValue(idEncoded, value.getValue().getDoubleValue(), value.getTimestamp(),
									(byte) value.getQuality().getQuality(), configuration);
						}
					} catch (IOException e) {
						logger.error("", e);
					} catch (IllegalConversionException e) {
						logger.error("", e);
					} catch (NullPointerException e) {
						logger.error("NPE in SlotsdbStorage - recorder: {}, proxy: {}, id: {}, config: {}, value: {}", 
								recorder, (recorder!=null? recorder.getProxy(): null), id, configuration, value,e);
					} finally {
						lock.writeLock().unlock();
					}

					return null;
				}

			});
		} catch (PrivilegedActionException e) {
			logger.error("", e);
		}

	}

	@Override
	public void insertValues(final List<SampledValue> values) throws DataRecorderException {
		if (recorder.getProxy().isReadOnlyMode())
			throw new AccessControlException("Database has been opened in read-only mode");

		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

				@Override
				public Void run() throws Exception {
					lock.writeLock().lock();
					try {
						if (configuration != null) {

							for (SampledValue value : values) {
								recorder.getProxy().appendValue(idEncoded, value.getValue().getDoubleValue(), value.getTimestamp(),
										(byte) value.getQuality().getQuality(), configuration);
							}
						}
					} catch (IOException e) {
						logger.error("", e);
					} catch (IllegalConversionException e) {
						logger.error("", e);
					} finally {
						lock.writeLock().unlock();
					}
					return null;
				}

			});
		} catch (PrivilegedActionException e) {
			logger.error("", e);
		}
	}

	@Override
	public List<SampledValue> getValues(final long startTime) {

		try {
			return AccessController
					.doPrivileged(new PrivilegedExceptionAction<List<SampledValue>>() {

						@Override
						public List<SampledValue> run() throws Exception {

							lock.readLock().lock();
							try {
								// long storageInterval = configuration.getFixedInterval();
//								records = recorder.getProxy().read(id, startTime, System.currentTimeMillis(), configuration);
//								if (recorder.clock != null)
//									records = recorder.getProxy().read(id, startTime, recorder.clock.getExecutionTime(), configuration);
//								else // only relevant for tests
//									records = recorder.getProxy().read(id, startTime,  System.currentTimeMillis(), configuration);
								return recorder.getProxy().read(idEncoded, startTime, Long.MAX_VALUE, configuration);
							} catch (IOException e) {
								logger.error("", e);
								return null;
							} finally {
								lock.readLock().unlock();
							}
						}

					});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}

	}

	@Override
	public List<SampledValue> getValues(final long startTime, final long endTime) {

		try {
			return AccessController
					.doPrivileged(new PrivilegedExceptionAction<List<SampledValue>>() {

						@Override
						public List<SampledValue> run() throws Exception {

							// --------------------------

							List<SampledValue> records = null;
							lock.readLock().lock();
							try {
								records = recorder.getProxy().read(idEncoded, startTime, endTime - 1, configuration);
							} catch (IOException e) {
								logger.error("", e);
							} finally {
								lock.readLock().unlock();
							}
							return records;

							// --------------------------

						}

					});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}

	}

	@Override
	public SampledValue getValue(final long timestamp) {

		try {
			return AccessController.doPrivileged(new PrivilegedExceptionAction<SampledValue>() {

				@Override
				public SampledValue run() throws Exception {

					// ------
					lock.readLock().lock();
					try {
						return recorder.getProxy().read(idEncoded, timestamp, configuration);
					} catch (IOException e) {
						logger.error("", e);
						return null;
					} finally {
						lock.readLock().unlock();
					}

					// ------
				}

			});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}

	}

	// TODO more efficient implementation; might as well return an iterator?
	@Override
	public List<SampledValue> getValues(final long startTime, final long endTime, final long intervalSize,
			final ReductionMode mode) {

		// last timestamp is exclusive and therefore not part of the request
		final long endTimeMinusOne = endTime - 1;

		try {
			return AccessController
					.doPrivileged(new PrivilegedExceptionAction<List<SampledValue>>() {

						@Override
						public List<SampledValue> run() throws Exception {

							// ----------------

							// TODO could cause crash? extremely long requested time period of very small sampled values
							List<SampledValue> returnValues = new ArrayList<SampledValue>();

							if (validateArguments(startTime, endTimeMinusOne, intervalSize)) {

								// Issues to consider:
								// a) calling the for each subinterval will slow down the reduction (many file accesses)
								// b) calling the read for the whole requested time period might be problematic -
								// especially when requested
								// time
								// period is large and the log interval was very small (large number of values)
								// Compromise: When the requested time period covers multiple days and therefore
								// multiple log files, then a
								// separate read data processing is performed for each file.
								final List<SampledValue> loggedValues;
								lock.readLock().lock();
								try {
									loggedValues = getLoggedValues(startTime, endTimeMinusOne);
								} finally {
									lock.readLock().unlock();
								}
//								List<SampledValue> loggedValues = removeQualityBad(loggedValuesRaw);

								if (loggedValues.isEmpty()) {
									// return an empty list since there are no logged values, so it doesn't make sense
									// to aggregate anything
									return returnValues;
								}

								// FIXME very inefficient! Better do this immediately in getLoggedValues!
								if (mode.equals(ReductionMode.NONE)) {
									return removeQualityBad(loggedValues);
								}

								List<Interval> intervals = generateIntervals(startTime, endTimeMinusOne, intervalSize);
								returnValues = generateReducedData(intervals, loggedValues.iterator(), mode);

							}

							return returnValues;

							// ----------------
						}

					});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}
	}

	/**
	 * Generates equidistant intervals starting from periodStart till periodEnd. The last interval might have a
	 * different length than intervalSize.
	 * 
	 * ASSUMPTION: Arguments are valid (see validateArguments method)
	 * 
	 * @return List of intervals which cover the entire period
	 */
	private static List<Interval> generateIntervals(long periodStart, long periodEnd, long intervalSize) {

		List<Interval> intervals = new ArrayList<Interval>();

		long start = periodStart;
		long end;
		do {
			end = start + intervalSize - 1;
			if (end > periodEnd) {
				end = periodEnd;
			}
			intervals.add(new Interval(start, end));
			start = end + 1;
		} while (end != periodEnd);

		return intervals;
	}

	private static List<SampledValue> generateReducedData(List<Interval> intervals, List<SampledValue> loggedValues, ReductionMode mode) {

		List<SampledValue> returnValues = new ArrayList<SampledValue>();
		List<SampledValue> reducedValues;

		ReductionFactory reductionFactory = new ReductionFactory();
		Reduction reduction = reductionFactory.getReduction(mode);

		int index = 0; // used to move forwards in the loggedValues list
		int maxIndex = loggedValues.size() - 1;
		SampledValue loggedValue = loggedValues.get(index);
		long timestamp = loggedValue.getTimestamp();

		Iterator<Interval> it = intervals.iterator();
		Interval interval;

		while (it.hasNext()) {

			interval = it.next();

			while (timestamp >= interval.getStart() && timestamp <= interval.getEnd()) {

				if (loggedValue.getQuality() == Quality.GOOD)
					interval.add(loggedValue);

				if (index < maxIndex) {
					index++;
					loggedValue = loggedValues.get(index);
					timestamp = loggedValue.getTimestamp();
				}
				else {
					// complete loggedValues list is processed
					break;
				}
			}

			reducedValues = reduction.performReduction(interval.getValues(), interval.getStart());
			returnValues.addAll(reducedValues);

		}

		// debug_printIntervals(intervals);

		return returnValues;
	}
	
	// ignores bad quality values
	private static List<SampledValue> generateReducedData(List<Interval> intervals, Iterator<SampledValue> loggedValues, ReductionMode mode) {

		final List<SampledValue> returnValues = new ArrayList<SampledValue>();
		if (!loggedValues.hasNext())
			return returnValues;
		List<SampledValue> reducedValues;

		final ReductionFactory reductionFactory = new ReductionFactory();
		final Reduction reduction = reductionFactory.getReduction(mode);

		
		SampledValue loggedValue = loggedValues.next();
		long timestamp = loggedValue.getTimestamp();

		Iterator<Interval> it = intervals.iterator();
		Interval interval;
		boolean done = false;
		while (it.hasNext()) {

			interval = it.next();
			
			while (   timestamp >= interval.getStart() && timestamp <= interval.getEnd()) {

				if (loggedValue.getQuality() == Quality.GOOD)
					interval.add(loggedValue);
				if (!loggedValues.hasNext()) {
					done = true;
					break;
				}
				loggedValue = loggedValues.next();
				timestamp = loggedValue.getTimestamp();
				
			}

			reducedValues = reduction.performReduction(interval.getValues(), interval.getStart());
			returnValues.addAll(reducedValues);
			if (done) 
				break;
		}
		while (it.hasNext()) {
			interval = it.next();
			reducedValues = reduction.performReduction(interval.getValues(), interval.getStart());
			returnValues.addAll(reducedValues);
		}

		// debug_printIntervals(intervals);

		return returnValues;
	}

	
	@SuppressWarnings("unused")
	private static void debug_printIntervals(List<Interval> intervals) {

		Iterator<Interval> it2 = intervals.iterator();
		Interval interval2;

		int i = 1;
		while (it2.hasNext()) {
			interval2 = it2.next();
			System.out.println(i + ": " + interval2.getValues().toString());
			i++;
		}
	}

	private static boolean validateArguments(long startTime, long endTime, long interval) {
		boolean result = false;

		if (startTime > endTime) {
			logger.warn("Invalid parameters: Start timestamp musst be smaller than end timestamp");
		}
		else if (interval < 0) {
			logger.warn("Invalid arguments: interval must be > 0");
		}
		else {
			result = true;
		}

		return result;
	}

	/**
	 * 
	 * @param startTime
	 * @param endTime
	 * @param intervalSize
	 * @return List with average values on success, otherwise empty list.
	 */
	private List<SampledValue> getLoggedValues(long startTime, long endTime) {
		try {
			return recorder.getProxy().read(idEncoded, startTime, endTime, configuration);
		} catch (IOException e) { // FIXME is this clever?
			e.printStackTrace();
			return new ArrayList<SampledValue>();
		}
	}

	private static List<SampledValue> removeQualityBad(List<SampledValue> toReduce) {
		return toReduce.stream()
			.filter(sv -> sv.getQuality() != Quality.BAD)
			.collect(Collectors.toList());
	}

	@Override
	public void update(RecordedDataConfiguration configuration) throws DataRecorderException {
		setConfiguration(configuration);
	}

	@Override
	public void setConfiguration(RecordedDataConfiguration configuration) {
		if (recorder.getProxy().isReadOnlyMode())
			throw new UnsupportedOperationException("Database has been opened in read-only mode");

		lock.writeLock().lock();

		try {
			this.configuration = configuration;
			AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

				@Override
				public Void run() throws Exception {

					// -----------

					recorder.persistSlotsDbStorages();

					// -----------

					return null;
				}

			});
		} catch (PrivilegedActionException e) {
			logger.error("", e);
		} finally {
			lock.writeLock().unlock();
		}

	}

	@Override
	public RecordedDataConfiguration getConfiguration() {
		return configuration;
	}

	@Override
	public SampledValue getNextValue(final long time) {

		try {
			return AccessController.doPrivileged(new PrivilegedExceptionAction<SampledValue>() {

				@Override
				public SampledValue run() throws Exception {

					// ------
					lock.readLock().lock();
					try {
						return recorder.getProxy().readNextValue(idEncoded, time, configuration);
					} catch (IOException e) {
						logger.error("", e);
						return null;
					} finally {
						lock.readLock().unlock();
					}

					// ------
				}

			});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}

	}
	
	@Override
	public SampledValue getPreviousValue(final long time) {
		try {
			return AccessController.doPrivileged(new PrivilegedExceptionAction<SampledValue>() {

				@Override
				public SampledValue run() throws Exception {

					// ------
					lock.readLock().lock();
					try {
						return recorder.getProxy().readPreviousValue(idEncoded, time, configuration);
					} catch (IOException e) {
						logger.error("", e);
						return null;
					} finally {
						lock.readLock().unlock();
					}

					// ------
				}

			});

		} catch (PrivilegedActionException e) {
			logger.error("", e);
			return null;
		}
	}

	@Override
	public Long getTimeOfLatestEntry() {
		// TODO
		return null;
	}

	@Override
	public InterpolationMode getInterpolationMode() {
		return InterpolationMode.NONE;
	}

	@Override
	public boolean isEmpty() {
		return getNextValue(Long.MIN_VALUE) == null;
	}

	@Override
	public boolean isEmpty(long startTime, long endTime) {
		SampledValue sv = getNextValue(startTime);
		return (sv == null || sv.getTimestamp() > endTime);
	}

	@Override
	public int size() {
		return size(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	@Override
	public int size(final long startTime, final long endTime) {
		try {
			return AccessController.doPrivileged(new PrivilegedExceptionAction<Integer>() {

				@Override
				public Integer run() throws Exception {
					lock.readLock().lock();
					try {
						return recorder.getProxy().size(idEncoded, startTime, endTime);
					} finally {
						lock.readLock().unlock();
					}
				}

			});

		} catch (PrivilegedActionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Iterator<SampledValue> iterator() {
		return new SlotsDbIterator(idEncoded, recorder, lock);
	}

	@Override
	public Iterator<SampledValue> iterator(long startTime, long endTime) {
		return new SlotsDbIterator(idEncoded, recorder, lock, startTime, endTime);
	}

	@Override
	public String toString() {
		String result = "SlotsDbStorage " + id + ", storage type: " + (configuration == null ? null : configuration.getStorageType());
		if (configuration != null && configuration.getStorageType() == StorageType.FIXED_INTERVAL)
			result = result.concat(", interval: " + configuration.getFixedInterval() + " ms");
		return result;
	}

	private final void triggerTagsPersistence() {
		recorder.triggerTagsPersistence();
	}
	
	@Override
	public void setProperty(String key, String value) {
		setProperty(key, Collections.singletonList(value), true);
	}

	@Override
	public void addProperty(final String tag, final String value) {
		Objects.requireNonNull(value);
		addProperties(tag, Collections.singletonList(value));
	}
	
	void setProperty(final String tag, final Collection<String> values, final boolean triggerPersistence) {
		Objects.requireNonNull(tag);
		Objects.requireNonNull(values);
		values.forEach(v -> Objects.requireNonNull(v));
		synchronized (tags) {
			if (triggerPersistence)
				this.tags.put(tag, new CopyOnWriteArrayList<>(values));
			else
				this.tags.put(tag, (List<String>) values); // internal method
		}
		if (triggerPersistence)
			triggerTagsPersistence();
	}

	@Override
	public void addProperties(final String tag, final Collection<String> values) {
		Objects.requireNonNull(tag);
		Objects.requireNonNull(values);
		values.forEach(v -> Objects.requireNonNull(v));
		synchronized (tags) {
			final List<String> list0 = tags.get(tag);
			final List<String> list;
			if (list0 == null) {
				list = new CopyOnWriteArrayList<>(values);
				tags.put(tag, list);
			} else {
				list0.addAll(values.stream()
					.filter(v -> !list0.contains(v))
					.collect(Collectors.toList()));
			}
		}
		triggerTagsPersistence();
	}
	
	@Override
	public void setProperties(Map<String, Collection<String>> properties) {
		setProperties(properties, true);
	}
	
	void setProperties(final Map<String,Collection<String>> properties, final boolean triggerPersistence) {
		Objects.requireNonNull(properties);
		properties.entrySet().forEach(entry -> {
			Objects.requireNonNull(entry.getKey());
			Objects.requireNonNull(entry.getValue());
			entry.getValue().forEach(val -> Objects.requireNonNull(val));
		});
		synchronized (tags) {
			tags.clear();
			properties.entrySet().forEach(entry -> {
				this.tags.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
			});
		}
		if (triggerPersistence)
			triggerTagsPersistence();
	}
	
	@Override
	public void addProperties(Map<String, Collection<String>> properties) {
		Objects.requireNonNull(properties);
		properties.entrySet().forEach(entry -> {
			Objects.requireNonNull(entry.getKey());
			Objects.requireNonNull(entry.getValue());
			entry.getValue().forEach(val -> Objects.requireNonNull(val));
		});
		synchronized (tags) {
			properties.entrySet().forEach(entry -> {
				final List<String> list0 = tags.get(entry.getKey());
				if (list0 == null) {
					final List<String> list = new CopyOnWriteArrayList<>(entry.getValue());
					tags.put(entry.getKey(), list);
				} else {
					list0.addAll(entry.getValue().stream()
						.filter(v -> !list0.contains(v))
						.collect(Collectors.toList()));
				}
			});
		}
		triggerTagsPersistence();
	}
	
	@Override
	public List<String> getProperties(String key) {
		final List<String> list = tags.get(key);
		return list != null ? new ArrayList<>(list) : null;
	}

	@Override
	public Map<String, List<String>> getProperties() {
		final Map<String, List<String>> copy = new HashMap<>(tags.size());
		for (Map.Entry<String, List<String>> entry: tags.entrySet()) {
			copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return copy;
	}

	@Override
	public boolean removeProperty(String tag) {
		final boolean result;
		synchronized (tags) {
			result = this.tags.remove(tag) != null;
		}
		if (result)
			triggerTagsPersistence();
		return result;
	}
	
	@Override
	public boolean removeProperty(String tag, String value) {
		final boolean result;
		synchronized (tags) {
			final List<String> values = tags.get(tag);
			if (values == null || values.isEmpty())
				return false;
			result = values.remove(value);
			if (result && values.isEmpty())
				tags.remove(tag);
		}
		if (result)
			triggerTagsPersistence();
		return result;
	}
	
	@Override
	public boolean hasProperty(String key) {
		return this.tags.containsKey(key);
	}
	
	// TODO
	@Override
	public boolean hasProperty(String tag, boolean regexpMatching) {
		return this.tags.containsKey(tag);
	}
	
	@Override
	public String getFirstProperty(String key) {
		final List<String> list = this.tags.get(key);
		return list != null && !list.isEmpty() ? list.get(0) : null;
	}
	
}

class Interval {

	final List<SampledValue> values = new ArrayList<SampledValue>();

	private final long start;
	private final long end;

	public Interval(long start, long end) {
		this.start = start;
		this.end = end;
	}

	public long getEnd() {
		return end;
	}

	public long getStart() {
		return start;
	}

	public void add(SampledValue value) {
		values.add(value);
	}

	public List<SampledValue> getValues() {
		return values;
	}
}