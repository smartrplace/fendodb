/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.ogema.core.administration.FrameworkClock;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.impl.InfoTask.DeleteJob;
import org.smartrplace.logging.fendodb.impl.InfoTask.Flusher;
import org.smartrplace.logging.fendodb.impl.InfoTask.SizeWatcher;

public final class FileObjectProxy {

	private static final int FLEXIBLE_STORING_PERIOD = -1;

	final static Logger logger = LoggerFactory.getLogger(FileObjectProxy.class);
	
	/**
	 * For creation and deletion (write lock), resp. parsing (read lock), of folders (i.e. days)
	 */
	final ReadWriteLock folderLock = new ReentrantReadWriteLock();
	
//	private final File rootNode;
	final Path rootNode;
	final String rootNodeString;
	// remove values only when folder write lock is held.
	// read lock is sufficient for adding values
	final Cache<String, FileObjectList> openFilesHM;

	Map<Path, FileSystem> zipFiles = new ConcurrentHashMap<>();
	// concurrent map
//	private final ConcurrentMap<String, String> encodedLabels = new ConcurrentHashMap<>();
	final Timer timer;
	// synchronized using folderLock
	List<Path> days;
	// synchronized using folderLock
	private long currentDay = Long.MAX_VALUE;
	// can be null, if data is written to disk immediately
	private final Flusher flusher;
	private final DeleteJob deleteJob;
	private final SizeWatcher sizeWatcher;
	private final FrameworkClock clock;
	final TemporalUnit unit;
	private final boolean readOnlyMode;
	final boolean useCompatibilityMode;
	
	/*
	 * Flush Period in Seconds. if flush_period == 0 -> write directly to disk.
	 */
	final int limit_folders;
	final int limit_size;
	private final int max_open_files;
	private final long dataExpirationCheckInterval;
	private final boolean readFolders;
	
	private final FendoCache cache;

	/**
	 * Creates an instance of a FileObjectProxy<br>
	 * @param rootNodePath
	 * @param clock
	 * 		may be null, in which case system time is used
	 * @param config
	 * @throws IOException
	 */
	public FileObjectProxy(Path rootNodePath, FrameworkClock clock, FendoDbConfiguration config) throws IOException {
		this.cache = config.isCacheDisabled() ? FendoCache.noopCache() : new SlotsDbCache();
		this.useCompatibilityMode = config.useCompatibilityMode();
		this.unit = useCompatibilityMode ? ChronoUnit.DAYS : config.getFolderCreationTimeUnit();
		this.readOnlyMode = config.isReadOnlyMode();
		this.readFolders = config.isReadFolders();
		this.clock = clock;
		if (config.getFlushPeriod() > 0 || config.getDataLifetimeInDays() > 0 || config.getMaxDatabaseSize() > 0 || config.getReloadDaysInterval() > 0)
			timer = new Timer();
		else
			timer = null;
		long checkItv  = config.getDataExpirationCheckInterval();
		if (checkItv > 0 && checkItv < 5 * 60 * 1000)
				checkItv = 5 * 60 * 1000;
		dataExpirationCheckInterval = checkItv;
		logger.info("Storing to: {}", rootNodePath);
		rootNode = rootNodePath;
		rootNodeString = rootNodePath.toString();
		days = loadDays(rootNodePath, useCompatibilityMode);
		// FIXME if opened in read only mode, no tasks are needed
		final long flushPeriod = config.getFlushPeriod();
		if (flushPeriod > 0) {
			final long flush_period;
			if (flushPeriod < 1000)
				flush_period = 1000;
			else
				flush_period = flushPeriod;
			logger.info("Flushing Data every: " + (flush_period/1000) + "s. to disk.");
			flusher = createScheduledFlusher(flush_period);
		}
		else {	
			logger.info("No Flush Period set. Writing Data directly to disk.");
			flusher = null;
		}
		final int dataLifetimeDays = config.getDataLifetimeInDays();
		if (dataLifetimeDays > 0) {
			limit_folders = dataLifetimeDays;
			logger.info("Maximum lifetime of stored Values: " + limit_folders + " Days.");
			deleteJob = createScheduledDeleteJob();
		}
		else {
			logger.info("Maximum lifetime of stored Values: UNLIMITED Days.");
			deleteJob = null;
			limit_folders = 0;
		}
		logger.info("Folder period: {}.", unit);
		final int maxDbSize = config.getMaxDatabaseSize();
		if (maxDbSize > 0) {
			if (maxDbSize < FendoDbConfiguration.MINIMUM_DATABASE_SIZE) 
				limit_size = FendoDbConfiguration.MINIMUM_DATABASE_SIZE;
			else
				limit_size = maxDbSize;
			logger.info("Size Limit: " + limit_size + " MB.");
			sizeWatcher = createScheduledSizeWatcher();
		}
		else {
			logger.info("Size Limit: UNLIMITED MB.");
			sizeWatcher = null;
			limit_size = 0;
		}

		final int maxOpen = config.getMaxOpenFolders();
		max_open_files = maxOpen >= 8 ? maxOpen : 8;
		
		RemovalListener<String, FileObjectList> reml = rn -> {
			try {
				logger.trace("open files cache: removed list for id {}", rn.getKey());
				rn.getValue().closeAllFiles();
			} catch (IOException ioex) {
				logger.debug("exception closing files on evicted cache element", ioex);
			}
		};
		openFilesHM = CacheBuilder.<String, FileObjectList>newBuilder().maximumSize(max_open_files)
				.removalListener(reml).build();

		logger.info("Maximum open Files for Database changed to: " + max_open_files);
	}
	
	public void close() {
		if (flusher != null)
			flusher.stopTask();
		if (deleteJob != null)
			deleteJob.stopTask();
		if (sizeWatcher != null)
			sizeWatcher.stopTask();
		if (timer != null)
			timer.cancel();
		folderLock.writeLock().lock();
		try {
			clearOpenFilesHashMap();
		} catch (IOException e) {
			logger.warn("Closing log files failed",e);
		} finally {
			folderLock.writeLock().unlock();
		}
		zipFiles.forEach((p,fs) -> {
			try {
				fs.close();
			} catch (IOException ioex) {
				logger.warn("zip filesystem close failed for {}: {}", p, ioex.getMessage());
			}
		});
//		encodedLabels.clear();
			
	}
	
	final long getTime() {
		return clock != null ? clock.getExecutionTime() : System.currentTimeMillis();
	}
	
	final boolean isReadOnlyMode() {
		return readOnlyMode;
	}
	
	final DeleteJob getDeleteJob() {
		if (deleteJob != null)
			return deleteJob;
		return new DeleteJob(this);
	}
	

	/**
	 * Requires folder write lock
	 * @return list of new days
	 * @throws IOException
	 */
	final List<Path> reloadDays() throws IOException {
		cache.clearCache();
		clearOpenFilesHashMap();
		final List<Path> oldDays = this.days;
		final int oldSize = oldDays.size();
		this.days = loadDays(rootNode, useCompatibilityMode);
		final List<Path> newDays = days.stream()
			.filter(d -> !oldDays.contains(d))
			.collect(Collectors.toList());
		if (readFolders) {
			final int newSize = days.size();
			checkEncodings(Math.min(newSize, newSize-oldSize+1));
		}
		return newDays;
	}
	
	private final void checkEncodings(int lastXDays) throws IOException {
		for (int i = 0; i < lastXDays; i++) {
			final Path day = days.get(days.size()-i-1);
			try (final Stream<Path> stream = Files.list(day)) {
				final List<Path> wrongEncodings = stream.filter(Files::isDirectory)
					.filter(fl -> fl.getFileName().toString().contains("%252F"))
					.collect(Collectors.toList());
				if (!wrongEncodings.isEmpty()) {
					clearOpenFilesHashMap();
					wrongEncodings.forEach(fl -> {
						try {
							final Path target = fl.getParent().resolve(URLDecoder.decode(fl.getFileName().toString(), "UTF-8"));
							if (Files.isDirectory(target))
								FileUtils.deleteDirectory(target.toFile());
							Files.move(fl, target, StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
				}
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}
	}
	
	/*
	 * loads a sorted list of all days in SLOTSDB. Necessary for search- and delete jobs.
	 */
	private List<Path> loadDays(final Path rootNode, final boolean useCompatibilityMode) throws IOException {
		try (final Stream<Path> stream = Files.list(rootNode)) {
			List<Path> rval = stream
					//.filter(f -> Files.isDirectory(f))
					.filter(f -> isDayFolder(f, useCompatibilityMode))
					.map(f -> {
						// for zipfiles, return the path inside the zip
						if (f.toString().endsWith(".zip")) {
							FileSystem zipfs = zipFiles.computeIfAbsent(f, zip -> {
								try {
									return FileSystems.newFileSystem(zip, FileObjectProxy.class.getClassLoader());
								} catch (IOException ex) {
									return null;
								}
							});
							Path zippedDayPath = zipfs.getPath(getFolderDateString(f));
							return zippedDayPath;
						}
						return f;
					})
					.sorted(!useCompatibilityMode ? daysComparator : daysComparatorCompat)
					.collect(Collectors.toList());
			if (!rval.isEmpty()) {
				logger.debug("days loaded: {}, start={}, end={}", rval.size(), rval.get(0), rval.get(rval.size()-1));
			} else {
				logger.debug("no days found");
			}
			return rval;
		}
	}
	
	private static boolean isDayFolder(final Path file, final boolean useCompatibilityMode) {
		try {
			String filename = file.getFileName().toString();
			if (filename.endsWith(".zip")) {
				filename = filename.substring(0, filename.length()-4);
			}
			if (!useCompatibilityMode) {
				Long.parseLong(filename);
			} else {
				LocalDate.from(TimeUtils.formatter.parse(filename));
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	private static Comparator<Path> daysComparator = new Comparator<Path>() {
		
		@Override
		public int compare(final Path o1, final Path o2) {
			if (o1.equals(o2))
				return 0;
			try {
				return Long.compare(Long.parseLong(o1.getFileName().toString()), Long.parseLong(o2.getFileName().toString()));
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		
	};
	
	private final static Comparator<Path> daysComparatorCompat = new Comparator<Path>() {
		
		@Override
		public int compare(final Path o1, final Path o2) {
			if (o1.equals(o2))
				return 0;
			try {
				final LocalDate date1 = LocalDate.from(TimeUtils.formatter.parse(o1.getFileName().toString()));
				final LocalDate date2 = LocalDate.from(TimeUtils.formatter.parse(o2.getFileName().toString()));
				return date1.compareTo(date2);
			} catch (DateTimeException e) {
				return 0;
			}
		}
		
	};

	/**
	 * Creates a Thread, that causes Data Streams to be flushed every x-seconds.<br>
	 * Define flush-period in seconds with JVM flag: org.ogema.recordeddata.slotsdb.flushperiod
	 */
	private Flusher createScheduledFlusher(long flush_period) {
		final Flusher f = new Flusher(this);
//		timer.schedule(f, flush_period * 1000, flush_period * 1000);
		timer.schedule(f, flush_period, flush_period);
		return f;
	}
	

	private DeleteJob createScheduledDeleteJob() {
		final DeleteJob dj = new DeleteJob(this);
		timer.schedule(dj, FendoDbConfiguration.INITIAL_DELAY, dataExpirationCheckInterval);
		return dj;
	}

	private SizeWatcher createScheduledSizeWatcher() {
		final SizeWatcher zw = new SizeWatcher(this);
		timer.schedule(zw, FendoDbConfiguration.INITIAL_DELAY, dataExpirationCheckInterval);
		return zw;
	}

	/**
	 * Appends a new Value to Slots Database.
	 * 
	 * @param label
	 * @param value
	 * @param timestamp
	 * @param state
	 * @param storingPeriod
	 * @throws IOException
	 */
	public void appendValue(String label, double value, long timestamp, byte state, RecordedDataConfiguration configuration) throws IOException {
		appendValue(label, value, timestamp, state, configuration, false);
	}

	private void appendValue(final String label, final double value, final long timestamp, final byte state,
			final RecordedDataConfiguration configuration, boolean lockForWriting) throws IOException {

		long storingPeriod;
		if (configuration.getStorageType().equals(StorageType.FIXED_INTERVAL)) {
			// fixed interval
			storingPeriod = configuration.getFixedInterval();
		}
		else {
			/* flexible interval */
			storingPeriod = FLEXIBLE_STORING_PERIOD;
		}

		FileObject toStoreIn = null;
		final long strDate = TimeUtils.getCurrentStart(timestamp, unit);
		//System.out.printf("timestamp=%d, strDate=%s%n", timestamp, strDate);
		
		final Lock lock = lockForWriting ? folderLock.writeLock() : folderLock.readLock();
		boolean lockReleased = false;
		
		final boolean requiresNewFolder;
		lock.lock();
		try {
			FileObjectList cachedList = openFilesHM.getIfPresent(label + strDate);
			requiresNewFolder = cachedList == null || cachedList.size() == 0;
			// in this case we need to abort the current operation and start again, this time holding the write lock
			if (requiresNewFolder && !lockForWriting) {
				lockReleased = true; // do not unlock again in finally
				lock.unlock();
				appendValue(label, value, timestamp, state, configuration, true);
				return;
			}

			/*
			 * If there is no FileObjectList for this folder, a new one will be created. (This will be the first value
			 * stored for this day) Eventually existing FileObjectLists from the day before will be flushed and closed. Also
			 * the Hashtable size will be monitored, to not have too many opened Filestreams.
			 */
			if (requiresNewFolder) {
				newDayStarted(strDate);
				//controlHashtableSize(); is cleared anyway
				final FileObjectList first = getFileObjectList(strDate, label);
	
				/*
				 * If FileObjectList for this label does not contain any FileObjects yet, a new one will be created. Data
				 * will be stored and List reloaded for next Value to store.
				 */
				if (first.size() == 0) {
	
					if (configuration.getStorageType().equals(StorageType.FIXED_INTERVAL)) {
						// fixed interval
						toStoreIn = new ConstantIntervalFileObject(rootNodeString + "/" + getDayFolderName(strDate) + "/" + label + "/c"
								+ timestamp + SlotsDb.FILE_EXTENSION, cache.getCache(label, "c" + timestamp  + SlotsDb.FILE_EXTENSION));
					}
					else {
						/* flexible interval */
						toStoreIn = new FlexibleIntervalFileObject(rootNodeString + "/" + getDayFolderName(strDate) + "/" + label + "/f"
								+ timestamp + SlotsDb.FILE_EXTENSION, cache.getCache(label, "f" + timestamp  + SlotsDb.FILE_EXTENSION));
					}
	
					long roundedTimestamp = getRoundedTimestamp(timestamp, configuration);
					toStoreIn.createFileAndHeader(roundedTimestamp, storingPeriod);
					toStoreIn.append(value, roundedTimestamp, state);
					
					toStoreIn.close(); /* close() also calls flush(). */
					
					first.reLoadFolder(cache, label);
					return;
				}
			}
	
			/*
			 * There is a FileObjectList for this day.
			 */
			final FileObjectList listToStoreIn = openFilesHM.getIfPresent(label + strDate);
			if (listToStoreIn.size() > 0) {
				toStoreIn = listToStoreIn.getCurrentFileObject();
	
				/*
				 * If StartTimeStamp is newer then the Timestamp of the value to store, this value can't be stored.
				 */
				long roundedTimestamp = getRoundedTimestamp(timestamp, configuration);
				if (toStoreIn.getStartTimeStamp() >= roundedTimestamp) {
					return;
				}
			}
	
			if (toStoreIn == null) {
				throw new IllegalStateException("could not find log file");
			}
	
			/*
			 * The storing Period may have changed. In this case, a new FileObject must be created.
			 */
			if (toStoreIn.getStoringPeriod() == storingPeriod || toStoreIn.getStoringPeriod() == 0) {
				toStoreIn = listToStoreIn.getCurrentFileObject();
				long roundedTimestamp = getRoundedTimestamp(timestamp, configuration);
				toStoreIn.append(value, roundedTimestamp, state);
				if (flusher == null) {
					toStoreIn.flush();
				}
				else {
					return;
				}
			}
			else {
				/*
				 * Interval changed -> create new File (if there are no newer values for this day, or file)
				 */
				if (toStoreIn.getTimestampForLatestValue() < timestamp) {
					if (storingPeriod != FLEXIBLE_STORING_PERIOD) { /* constant intervall */
						toStoreIn = new ConstantIntervalFileObject(rootNodeString + "/" + getDayFolderName(strDate) + "/" + label + "/c"
								+ timestamp + SlotsDb.FILE_EXTENSION, cache.getCache(label, "c" + timestamp  + SlotsDb.FILE_EXTENSION));
					}
					else { /* flexible intervall */
						toStoreIn = new FlexibleIntervalFileObject(rootNodeString + "/" + getDayFolderName(strDate) + "/" + label + "/f"
								+ timestamp + SlotsDb.FILE_EXTENSION, cache.getCache(label, "f" + timestamp  + SlotsDb.FILE_EXTENSION));
					}
					toStoreIn.createFileAndHeader(timestamp, storingPeriod);
					toStoreIn.append(value, timestamp, state);
					if (flusher == null) {
						toStoreIn.flush();
					}
					listToStoreIn.reLoadFolder(cache, label);
				}
			}
		} finally {
			if (!lockReleased) {
				lock.unlock();
			}
		}
	}

	/**
	 * Rounds the timestamp to the next matching interval.
	 * 
	 * @param timestamp
	 *            the timestamp to round
	 * @param configuration
	 *            RecordedDataConfiguration
	 * @return
	 */
	public static long getRoundedTimestamp(long timestamp, RecordedDataConfiguration configuration) {
		if (configuration == null || configuration.getStorageType() != StorageType.FIXED_INTERVAL)
			return timestamp;
		final long stepInterval = configuration.getFixedInterval();
		if (stepInterval <= 0)
			throw new IllegalArgumentException("FixedInterval size needs to be greater than 0 ms");
		return getRoundedTimestamp(timestamp, stepInterval);
	}

	// FIXME shouldn't be accessible from outside. ConstantIntervalFileObjects needs this as workaround.
	public static long getRoundedTimestamp(final long timestamp, final long stepInterval) {
		long distance = timestamp % stepInterval;
		long diff = stepInterval - distance;
//		if (distance > stepInterval / 2) { // beware of value overflow...
		if ((distance > stepInterval / 2 && Long.MAX_VALUE - timestamp >= diff) || timestamp - distance < Long.MIN_VALUE) { 
			// go up 
			return timestamp - distance + stepInterval;
		}
		else {
			// go down
			return timestamp - distance;
		}
	}

//	private final String encodeLabel(final String label) {
//		return encodedLabels.computeIfAbsent(label, (key) -> computeEncodedLabel(key));
//	}
//	
//	final static String computeEncodedLabel(final String label) {
//		// encoding should be compatible with usual linux & windows file system file names
//		String encodedLabel;
//		try {
//			encodedLabel = URLEncoder.encode(label, "UTF-8");
//		} catch (UnsupportedEncodingException e) {
//			throw new RuntimeException(e);
//		}
//		// should be false in any reasonable system
//		boolean use252F = Boolean.getBoolean("org.ogema.recordeddata.slotsdb.use252F");
//		if (use252F) {
//			encodedLabel = encodedLabel.replace("%2F", "%252F");				
//		} else {
//			encodedLabel = encodedLabel.replace("%252F", "%2F");				
//		}
//		return encodedLabel;
//	}

	//Note: Comprehensive revision to fix the method. The previous version could just find data from the
	//current day
	public SampledValue readNextValue(final String label, final long t, final RecordedDataConfiguration configuration)
			throws IOException {
		long timestamp = t;
		FileObjectList folder;
		SampledValue result = null;
		folderLock.readLock().lock();
		try {
			folder = getNextFolder(label, timestamp, true);
			if (folder == null) {
				//System.out.printf("%s readNextValue(%s, %d) = null [0](no folder)%n", Thread.currentThread().getName(), label, t);
				return null;
			}
			List<FileObject> folList = folder.getFileObjectsStartingAt(timestamp);
			while (folList.isEmpty()) {
				//check next day // XXX should probably not happen
				folder = getNextFolder(label, folder, false);
				if (folder == null) {
					//System.out.printf("%s readNextValue(%s, %d) = null [1](no folder)%n", Thread.currentThread().getName(), label, t);
					return null;
				}
				folList = folder.getFileObjectsStartingAt(timestamp);
				if (folList == null) {
					//System.out.printf("%s readNextValue(%s, %d) = null [2](no folder)%n", Thread.currentThread().getName(), label, t);
					return null;
				}
			}
			FileObject toReadFrom = null;
			long minTime = Long.MAX_VALUE;
			for (FileObject toReadFrom2: folList) {
				if (toReadFrom2.startTimeStamp < minTime) {
					minTime = toReadFrom2.startTimeStamp;
					toReadFrom = toReadFrom2;
				}
			}
			//FileObject toReadFrom = folList.get(0).startTimeStamp;
			//FileObject toReadFrom = openFilesHM.get(label + strDate).getFileObjectForTimestamp(timestamp);
			
			if (toReadFrom != null) {
				timestamp = Math.max(timestamp,toReadFrom.getStartTimeStamp());
				result = toReadFrom.read(timestamp);
				if (result == null) {
					result = toReadFrom.readNextValue(timestamp); // null if no value for timestamp is available
				}
			}
		} finally {
			folderLock.readLock().unlock();
		}
		// this can happen if rounding takes place
		if (result != null && result.getTimestamp() < t) {
			long delta = configuration.getFixedInterval();
			if (delta <= 0) 
				throw new IllegalStateException("Fixed interval <= 0");
			timestamp += delta;
			if (timestamp <= t)
				timestamp += delta;
			return readNextValue(label, timestamp, configuration);
		}
		
		//System.out.printf("%s readNextValue(%s, %d) = %s [3]%n", Thread.currentThread().getName(), label, t, result);
		return result;
	
	}
	
	public SampledValue readPreviousValue(final String label, final long t, final RecordedDataConfiguration configuration) throws IOException {
		//long timestamp = configuration != null && configuration.getStorageType() == StorageType.FIXED_INTERVAL ? getRoundedTimestamp(t, configuration) : t;
		long timestamp = t;
		final List<FileObjectList> days;
		SampledValue result= null;
		folderLock.readLock().lock();
		try {
			days = getFoldersForIntervalSorted(label, Long.MIN_VALUE, timestamp);
		
			if (days.isEmpty()) 
				return null;
			List<FileObject> folList = null;
			for (int i=days.size()-1;i>=0;i--) {
				folList = days.get(i).getFileObjectsUntil(timestamp);
				if (!folList.isEmpty())
					break;
			}
			if (folList == null || folList.isEmpty())
				return null;
			FileObject toReadFrom = null;
			long maxTime = Long.MIN_VALUE;
			for(FileObject toReadFrom2: folList) {
				if (toReadFrom2.startTimeStamp > maxTime) {
					maxTime = toReadFrom2.startTimeStamp;
					toReadFrom = toReadFrom2;
				}
			}
			if (toReadFrom != null) {
				timestamp = Math.min(timestamp,toReadFrom.getTimestampForLatestValue());
				result = toReadFrom.read(timestamp);
				if (result == null) { 
					result = toReadFrom.readPreviousValue(timestamp); // null if no value for timestamp
				}
				// is available
			}
		} finally {
			folderLock.readLock().unlock();
		}
		// this can happen if rounding takes place
		if (result != null && result.getTimestamp() > t) {
			long delta = configuration.getFixedInterval();
			if (delta <= 0) 
				throw new IllegalStateException("Fixed interval <= 0");
			timestamp = timestamp- delta;
			if (timestamp >= t)
				timestamp =timestamp- delta;
			return readNextValue(label, timestamp, configuration);
		}
		return result;

	}

	public SampledValue read(final String label, long timestamp, final RecordedDataConfiguration configuration) throws IOException {
		// label = URLEncoder.encode(label,Charset.defaultCharset().toString());
		// //encodes label to supported String for Filenames.
		//timestamp = getRoundedTimestamp(timestamp, configuration);

//		String strDate = getStrDate(timestamp);

		// XXX we cannot do this in a method which must only hold the read lock; why is it here, but not in the other read methods?
//		if (!openFilesHM.containsKey(label + strDate)) {
//			controlHashtableSize();
//			FileObjectList fol = new FileObjectList(rootNodeString + "/" + strDate + "/" + label);
//			openFilesHM.put(label + strDate, fol);
//		}
		final FileObject toReadFrom;
		folderLock.readLock().lock();
		try {
			final FileObjectList fol = getFileObjectList(TimeUtils.getCurrentStart(timestamp, unit), label);
			if (fol == null)
				return null;
			toReadFrom = fol.getFileObjectForTimestamp(timestamp);
			if (toReadFrom != null) {
				return toReadFrom.read(timestamp); // null if no value for timestamp
				// is available
			}
		} finally {
			folderLock.readLock().unlock();
		}
		return null;
	}
	
	private final Comparator<Path> getParentDirComparator() {
		return useCompatibilityMode ? parentDirComparatorCompat : parentDirComparator;
	}
	
	private final static Comparator<Path> parentDirComparator = new Comparator<Path>() {

		@Override
		public int compare(final Path o1, final Path o2) {
			try {
				return Long.compare(Long.parseLong(o1.getParent().getFileName().toString()), Long.parseLong(o2.getParent().getFileName().toString()));
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	};
	
	private final static Comparator<Path> parentDirComparatorCompat = new Comparator<Path>() {

		@Override
		public int compare(final Path o1, final Path o2) {
			try {
				final long t1 = TimeUtils.parseCompatibilityFolderName(o1.getParent().getFileName().toString());
				final long t2 = TimeUtils.parseCompatibilityFolderName(o2.getParent().getFileName().toString());
				return Long.compare(t1, t2);
			} catch (DateTimeException e) {
				return 0;
			}
		}
	};
	
	// requires folder read lock 
	private List<FileObjectList> getFoldersForIntervalSorted(final String label, final long start, final long end) throws IOException {
		final long foldersStart = start == Long.MIN_VALUE ? start : TimeUtils.getCurrentStart(start, unit);
		return days.parallelStream()
			.filter(day -> isFolderBetweenStartAndEnd(day, foldersStart, end, useCompatibilityMode))
			.map(day -> day.resolve(label))
			.filter(subfolder -> Files.isDirectory(subfolder))
			.sorted(getParentDirComparator())
			.map(subfolder -> getFileObjectList(subfolder.getParent(), label))
			.filter(fol -> fol != null)
			.collect(Collectors.toList());
	}
	
	
	// requires folder read lock
	// label must be encoded already
	final FileObjectList getNextFolder(final String label, final long start, final boolean inclusive) throws IOException {
		final long actualStart = inclusive ? TimeUtils.getCurrentStart(start, unit) : start;
		final Optional<Path> opt = days.stream()
			.filter(day -> isFolderBetweenStartAndEnd(day, actualStart, Long.MAX_VALUE, useCompatibilityMode))
			.map(day -> day.resolve(label))
			.filter(subfolder -> Files.isDirectory(subfolder))
			.sorted(getParentDirComparator())
			.findFirst();
		//System.out.printf("%s getNextFolder(%s, %d): opt=%s%n", Thread.currentThread().getName(), label, start, opt);
		if (!opt.isPresent()) {
			return null;
		}
		final Path folder = opt.get();
		return getFileObjectList(folder.getParent(), label);
		
		/*
		 * Check for Folders matching criteria: Folder contains data between start & end timestamp. Folder contains
		 * label.
		 */
//		String strSubfolder;
//		List<File> folders = Arrays.asList(rootNode.listFiles());
//		Collections.sort(folders);
//		// TODO avoid iteration over all folders, use heuristics to guess start element, in case of large lists of folders
//		for (File folder : folders) {
//			if (folder.isDirectory()) {
//				if (isFolderBetweenStartAndEnd(folder.getName(), start, Long.MAX_VALUE)) {
//					if (Arrays.asList(folder.list()).contains(label)) {
//						strSubfolder = rootNodeString + "/" + folder.getName() + "/" + label;
//						if (logger.isTraceEnabled())
//							logger.trace(strSubfolder + " contains " + SlotsDb.FILE_EXTENSION + " files to read from.");
//						return new FileObjectList(strSubfolder, cache, label);
//					}
//					else if (Arrays.asList(folder.list()).contains(URLEncoder.encode(label,"UTF-8"))) {
//						strSubfolder = rootNodeString + "/" + folder.getName() + "/" + URLEncoder.encode(label,"UTF-8");
//						if (logger.isTraceEnabled())
//							logger.trace(strSubfolder + " contains " + SlotsDb.FILE_EXTENSION + " files to read from.");
//						return new FileObjectList(strSubfolder, cache, label);
//					}
//				}
//			}
//		}
//		return null;
	}

	// requires folder read lock 
	// label must be encoded
	FileObjectList getNextFolder(final String label, FileObjectList folder, final boolean inclusive) throws IOException {
		final String parentFolderName = Paths.get(folder.getFolderName()).getParent().getFileName().toString();
		final long t = !useCompatibilityMode ? Long.parseLong(parentFolderName) : 
				TimeUtils.parseCompatibilityFolderName(parentFolderName);
		try {
			return getNextFolder(label, t + (inclusive ? 0 : 1), inclusive);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to parse folder list {}", folder, e);
			return null;
		}
	}
	
	static List<SampledValue> readFolder(final FileObjectList folder) throws IOException {
		if (folder.size() == 1)
			return folder.getAllFileObjects().get(0).readFully();
		final List<SampledValue> values = new ArrayList<>();
		for (FileObject fo : folder.getAllFileObjects()) {
			values.addAll(fo.readFully());
		}
		return values;
	}
	
	/*
	 * Note: if start is too small (< minL) or end is too large (end > maxL), this fails to work.
	 * Essentially, the year must be a positive number with at most 4 digits.
	 */
	public List<SampledValue> read(final String label, long start, long end, final RecordedDataConfiguration configuration) throws IOException {
		if (logger.isTraceEnabled()) {
			logger.trace("Called: read(" + label + ", " + start + ", " + end + ")");
		}
		
		/*if (configuration != null && configuration.getStorageType() == StorageType.FIXED_INTERVAL) {
			start = getRoundedTimestamp(start, configuration);
			end = getRoundedTimestamp(end, configuration);
		}*/
//		List<SampledValue> toReturn = new Vector<SampledValue>();
		final List<SampledValue> toReturn = new ArrayList<>();
		if (start > end) {
			logger.trace("Invalid Read Request: startTS > endTS");
			return toReturn;
		}

		if (start == end) {
			logger.trace("start == end found");
			toReturn.add(read(label, start, configuration)); // let other read function handle.
			toReturn.removeAll(Collections.singleton(null));
			return toReturn;
		}
		// label = URLEncoder.encode(label,Charset.defaultCharset().toString());
		// //encodes label to supported String for Filenames.
		final long strStartDate = TimeUtils.getCurrentStart(start, unit);
		final long strEndDate =  TimeUtils.getCurrentStart(end, unit);
		
		List<FileObject> toRead = new ArrayList<>();

		folderLock.readLock().lock();
		if (logger.isTraceEnabled())
			logger.trace("Found startDate:"+strStartDate+" endDate:"+strEndDate);
		try {
			if (strStartDate != strEndDate) {
				logger.trace("Reading Multiple Days. Scanning for Folders.");
				final List<FileObjectList> days = getFoldersForIntervalSorted(label, start, end);
				/*
				 * Create a list with all file-objects that must be read for this reading request.
				 */
				if (days.size() == 0) {
					return toReturn;
				}
				else if (days.size() == 1) {
					toRead.addAll(days.get(0).getFileObjectsFromTo(start, end));
				}
				else { // days.size()>1
					toRead.addAll(days.get(0).getFileObjectsStartingAt(start));
					for (int i = 1; i < days.size() - 1; i++) {
						toRead.addAll(days.get(i).getAllFileObjects());
					}
					toRead.addAll(days.get(days.size() - 1).getFileObjectsUntil(end));
				}
				toRead.removeAll(Collections.singleton(null));
			}
			else { // Start == End Folder -> only 1 FileObjectList must be read.
				if (logger.isTraceEnabled())
					logger.trace("Before getFileObjectList for "+label);
				final FileObjectList fol = getFileObjectList(strStartDate, label);
				if (logger.isTraceEnabled())
					logger.trace("FileObjectListSize:"+fol.size());
				if (fol.size() > 0)
					toRead.addAll(fol.getFileObjectsFromTo(start, end));
			}
		
			if (logger.isTraceEnabled())
				logger.trace("Found " + toRead.size() + " " + SlotsDb.FILE_EXTENSION + " files to read from.");

			/*
			 * Read all FileObjects: first (2nd,3rd,4th....n-1) last first and last will be read separately, to not exceed
			 * timestamp range.
			 */
			if (toRead != null) {
				if (toRead.size() > 1) {
					toReturn.addAll(toRead.get(0).read(start, toRead.get(0).getTimestampForLatestValue()));
					for (int i = 1; i < toRead.size() - 1; i++) {
						toReturn.addAll(toRead.get(i).readFully());
					}
					toReturn.addAll(toRead.get(toRead.size() - 1).read(toRead.get(toRead.size() - 1).getStartTimeStamp(), end));
	
					/*
					 * Some Values might be null -> remove
					 */
					toReturn.removeAll(Collections.singleton(null));
	
				}
				else if (toRead.size() == 1) { // single FileObject
					toReturn.addAll(toRead.get(0).read(start, end));
					toReturn.removeAll(Collections.singleton(null));
				}
			}
		} finally {
			folderLock.readLock().unlock();
		}
		if (logger.isTraceEnabled())
			logger.trace("Selected " + SlotsDb.FILE_EXTENSION + " files contain " + toReturn.size() + " Values.");
		return toReturn;
	}
	
	static String getFolderDateString(Path folder) {
		String folderName = folder.getFileName().toString();
		if (folderName.endsWith(".zip")) {
			return folderName.substring(0, folderName.length()-4);
		} else {
			return folderName;
		}
	}

	/**
	 * Parses a Timestamp in Milliseconds from a String in yyyyMMdd Format <br>
	 * e.g.: 25.Sept.2011: 20110925 <br>
	 * would return: 1316901600000 ms. equal to (25.09.2011 - 00:00:00) <br>
	 * 
	 * @param name
	 *            in "yyyyMMdd" Format -> in ms format
	 * @param start
	 * @param end
	 * @return
	 * @throws IOException
	 */
	private static final boolean isFolderBetweenStartAndEnd(final Path name, final long start, final long end, final boolean useCompatibilityMode) {
		final long folderStart;
		String folderName = getFolderDateString(name);
		try {
			folderStart = !useCompatibilityMode ? Long.parseLong(folderName) : TimeUtils.parseCompatibilityFolderName(folderName);
		} catch (NumberFormatException | DateTimeException e) {
			logger.error("Unable to parse Timestamp from folder {}",name,e);
			return false;
		}
		return start <= folderStart && folderStart <= end;
			
//		return start <= folderStart + 86399999 && folderStart <= end;
//		final SimpleDateFormat sdf = getDateFormat();
//		try {
//			sdf.parse(name);
//		} catch (ParseException e) {
//			logger.error("Unable to parse Timestamp from: " + name + " folder. " + e.getMessage());
//		}
//		if (start <= sdf.getCalendar().getTimeInMillis() + 86399999 && sdf.getCalendar().getTimeInMillis() <= end) { // if
//			// start
//			// <=
//			// folder.lastTSofDay
//			// &&
//			// folder.firstTSofDay
//			// <=
//			// end
//			return true;
//		}
//		return false;
	}
	
	private FileObjectList getFileObjectList(final Path dayFolder, final String label) {
		try {
			final long dayStr = !useCompatibilityMode ? Long.parseLong(dayFolder.getFileName().toString()) : 
				TimeUtils.parseCompatibilityFolderName(dayFolder.getFileName().toString());
			return getFileObjectList(dayStr, label);
		} catch (NumberFormatException | NullPointerException e) {
			logger.error("Invalid folder passed: {}",dayFolder, e);
			return null;
		}
	}

	/**
	 * Requires folder read lock to be held
	 * @param day
	 * @param label
	 * @return
	 */
	private FileObjectList getFileObjectList(final long day, String label) {
		try {
			final String id = label + day;
			
			//FIXME only works for length 255+ ogema labels
			String labelFsPath
					= label.length() > 255 && label.contains("%")
					? URLDecoder.decode(label, "UTF-8")
					: label;
			
			FileObjectList rval = openFilesHM.get(id, () -> {
				try {
					controlHashtableSize();
					String dayFolderName = getDayFolderName(day);

					logger.trace("creating and caching list for id {}, cache size={}", id, openFilesHM.size());
					Path zipPath = isZipped(dayFolderName, label);
					if (zipPath != null) {
						logger.debug("found zipped data for {}/{}: {}", day, label, zipPath);
						return new FileObjectList(zipPath,
							rootNodeString + "/" + dayFolderName + "/" + labelFsPath,
							dayFolderName, cache, label, useCompatibilityMode);
					}

					return new FileObjectList(
							rootNodeString + "/" + dayFolderName + "/" + labelFsPath,
							dayFolderName, cache, label, useCompatibilityMode);
				} catch (IOException e) {
					logger.error("Failed to construct FileObjectList",e);
					return null;
				}
			});
			//System.out.printf("%s getFileObjectList(%d, %s) = %s%n", Thread.currentThread().getName(), day, label, rval);
			return rval;
		} catch (ExecutionException ee) {
			logger.warn("exception", ee);
			return null;
		} catch (UnsupportedEncodingException ex) {
			return null;
		}
	}
	
	Path isZipped(String day, String label) throws IOException {
		Path zipFile = //Path.of(rootNodeString, day + ".zip");
				FileSystems.getDefault().getPath(rootNodeString, day + ".zip");
		if (!Files.exists(zipFile)) {
			return null;
		}
		if (zipFiles.containsKey(zipFile)) {
			return zipFiles.get(zipFile).getPath(day, label);
		}
		FileSystem zipfs = zipFiles.get(zipFile);
		if (zipfs == null) {
			//zipfs = FileSystems.newFileSystem(zipFile, getClass().getClassLoader());
			// keep open zip files in map, zip path from closed zip fs will not work
			//zipFiles.put(zipFile, zipfs);
			
			// keep open zip files in map, zip path from closed zip fs will not work
			zipfs = zipFiles.computeIfAbsent(zipFile, zip -> {
								try {
									return FileSystems.newFileSystem(zip, FileObjectProxy.class.getClassLoader());
								} catch (IOException ex) {
									return null;
								}
							});
		}
		Path dataseriesZipPath = zipfs.getPath(day, label);
		if (Files.exists(dataseriesZipPath)) {
			return dataseriesZipPath;
		}
		return null;
	}
	
	/*
	 * strCurrentDay holds the current Day in yyyyMMdd format, because SimpleDateFormat uses a lot cpu-time.
	 * currentDayFirstTS and ... currentDayLastTS mark the first and last timestamp of this day. If a TS exceeds this
	 * range, strCurrentDay, currentDayFirstTS, currentDayLastTS will be updated.
	 */

	/** 
	 * requires folder write lock
	 */
	private void newDayStarted(final long strDate) throws IOException {
//		if (openFilesHM.containsKey(label + strDate)) {
		if (strDate != currentDay) {
			currentDay = strDate;
			/*
			 * Value for new day has been registered! Close and flush all connections! Empty Hashtable!
			 */
		
			clearOpenFilesHashMap();
			logger.info("Started logging to a new Day. <{}> Folder has been closed and flushed completely.",getDayFolderName(strDate));
			Files.createDirectories(rootNode.resolve(getDayFolderName(strDate)));
			/* reload days */
			reloadDays();
		}
	}
	
	/** 
	 * requires folder write lock
	 */
	void clearOpenFilesHashMap() throws IOException {
		Iterator<FileObjectList> itr = openFilesHM.asMap().values().iterator();
		while (itr.hasNext()) { // kick out everything
			itr.next().closeAllFiles();
		}
		openFilesHM.invalidateAll();
	}
	
	void clearCache() {
		cache.clearCache();
	}
	
	public int size(String label, long start, long end) throws DataRecorderException, IOException {
		int size = 0;
		folderLock.readLock().lock();
		try {
			List<FileObjectList> folders = getFoldersForIntervalSorted(label, start, end);
			for (FileObjectList folder: folders) {
				for (FileObject file: folder.getAllFileObjects()) {
					size += file.getDataSetCount(start, end);
				}
			}
			return size;
		} catch (IOException e) {
			throw new DataRecorderException("",e);
		} finally {
			folderLock.readLock().unlock();
		}
	}
	
	volatile ForkJoinTask<Void> sizeControlTask;
	
	private void controlHashtableSize() {
		/*
		 * hm.size() doesn't really represent the number of open files, because it contains FileObjectLists, which may
		 * contain 1 ore more FileObjects. In most cases, there is only 1 File in a List. There will be a second File if
		 * storage Intervall is reconfigured. Continuous reconfiguring of measurement points may lead to a
		 * "Too many open files" Exception. In this case SlotsDb.MAX_OPEN_FOLDERS should be decreased...
		 */
		
		if (openFilesHM.size() < max_open_files)
			return;
		if (sizeControlTask != null && !sizeControlTask.isDone()) {
			return;
		}
		/*
		// execute in new thread to avoid deadlock due to read lock being held
		sizeControlTask = ForkJoinPool.commonPool().submit(() -> {
			if (folderLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
				try {
					if (openFilesHM.size() < max_open_files) { // double checked locking, simply to avoid running this too often
						return null;
					}
					controlHashtableSizeInternal();
				} catch (IOException e) {
					logger.error("Failed to close file?", e);
				} finally {
					folderLock.writeLock().unlock();
				}
			}
			return null;
		});
		*/
	}
	
	private final String getDayFolderName(final long timestamp) {
		return !useCompatibilityMode ? String.valueOf(timestamp) : TimeUtils.formatCompatibilityFolderName(timestamp);
	}
	
	int openFolders() {
		return (int) openFilesHM.size();
	}
	
}
