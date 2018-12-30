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
package org.smartrplace.logging.fendodb;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.recordeddata.DataRecorder;
import org.ogema.recordeddata.DataRecorderException;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.search.TimeSeriesMatcher;

/**
 * A database instance.
 */
public interface CloseableDataRecorder extends DataRecorder, AutoCloseable {

	@Override
	void close() throws IOException;

	List<FendoTimeSeries> getAllTimeSeries();

	/**
	 * Search for time series matching different kinds of filters.
	 * See {@link SearchFilterBuilder}.
	 * @param filter
	 * @return
	 */
	List<FendoTimeSeries> findTimeSeries(TimeSeriesMatcher filter);

	/**
	 * Note: this method deletes all data until the
	 * previous folder boundary before the passed time instant. Hence, it may delete
	 * less data than expected.
	 * @param instant
	 * @return
	 * @throws IOException
	 * @throws SecurityException if the caller does not have admin permission for this database
	 */
	boolean deleteDataBefore(Instant instant) throws IOException;

	/**
	 * Note: this method deletes all data until the previous folder
	 * boundary before the passed time instant. Hence, it may delete
	 * more data than expected.
	 * @param instant
	 * @return
	 * @throws IOException
 	 * @throws SecurityException if the caller does not have admin permission for this database
	 */
	boolean deleteDataAfter(Instant instant) throws IOException;

	/**
	 * Note: this method deletes all data until the previous folder
	 * boundary before the time instant obtained by subtracting the passed
	 * duration from now. Hence, it may delete less data than expected.
	 * @param duration
	 * @return
	 * @throws IOException
	 * @throws SecurityException if the caller does not have admin permission for this database
	 */
	boolean deleteDataOlderThan(TemporalAmount amount) throws IOException;

	/**
	 * Get all values for the specified property key in the database.
	 * @param key
	 * @return
	 */
	Collection<String> getAllPropertyValues(String key);

	/**
	 * Get a map with keys = all property keys of timeseries in the database,
	 * and values = collection of all values for the respective key.
	 * @return
	 */
	Map<String, Collection<String>> getAllProperties();

	@Override
	FendoTimeSeries getRecordedDataStorage(String id);

	@Override
	FendoTimeSeries createRecordedDataStorage(String id, RecordedDataConfiguration configuration) throws DataRecorderException;

	Path getPath();
	/**
	 * Get the (immutable) configuration object
	 * @return
	 */
	FendoDbConfiguration getConfiguration();
	/**
	 * Check whether the database has been closed down (see {@link #close()}).
	 * @return
	 */
	boolean isActive();

	boolean isEmpty();

	/**
	 * Note: this operation may be very time consuming. The database will not be operational during the update.
	 * This is particularly true if the new configuration has a different {@link FendoDbConfiguration#getFolderCreationTimeUnit() update interval}
	 * from the old one. This will {@link #close()} the underlying database object, unless the new configuration coincides with
	 * the old one, and the caller will have to continue working with the new database afterwards.
	 * If an exception occurs during this operation, the database will not be closed.
	 * @param newConfiguration
	 * 		not null
	 * @throws IOException
	 * @return
	 * 		a new database
	 */
	DataRecorderReference updateConfiguration(FendoDbConfiguration newConfiguration) throws IOException;

	/**
	 * Copy the slotsDb instance to a new location. The target folder must be empty or non-existent, in the latter case
	 * it will be created.
	 * @param target
	 * @param configuration
	 * 		may be null, in which case the configuration of the passed instance is used.
	 * @return
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * 		if target exists but is not a directory
	 * @throws IllegalStateException
	 * 		if target exists as a directory, but is non-empty
	 * @throws SecurityException
	 * 		if Java security is active, and the caller does not have the permission to
	 * 		create files under the specified path
	 * @throws NullPointerException
	 * 		if target is null
	 */
	DataRecorderReference copy(Path target, FendoDbConfiguration configuration) throws IOException;

	/**
	 * Copy the slotsDb instance to a new location. The target folder must be empty or non-existent, in the latter case
	 * it will be created.
	 * @param target
	 * @param configuration
	 * 		may be null, in which case the configuration of the passed instance is used.
	 * @return
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * 		if target exists but is not a directory, or if startTime is &ge; endTime
	 * @throws IllegalStateException
	 * 		if target exists as a directory, but is non-empty
	 * @throws SecurityException
	 * 		if Java security is active, and the caller does not have the permission to
	 * 		create files under the specified path
	 * @throws NullPointerException
	 * 		if target is null
	 */
	DataRecorderReference copy(Path target, FendoDbConfiguration configuration, long startTime, long endTime) throws IOException;

	/**
	 * Copy the slotsDb instance to a new location, define a custom filter for the timeseries to be included in the copy.
	 * The target folder must be empty or non-existent, in the latter case it will be created.
	 * @param target
	 * @param configuration
	 * 		may be null, in which case the configuration of the passed instance is used.
	 * @param filter
	 * 		a filter for the time series to be included in the copy.
	 * @return
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * 		if target exists but is not a directory, or if startTime is &ge; endTime
	 * @throws IllegalStateException
	 * 		if target exists as a directory, but is non-empty
	 * @throws SecurityException
	 * 		if Java security is active, and the caller does not have the permission to
	 * 		create files under the specified path
	 * @throws NullPointerException
	 * 		if target is null
	 */
	DataRecorderReference copy(Path target, FendoDbConfiguration configuration, TimeSeriesMatcher filter, long startTime, long endTime) throws IOException;


	/**
	 * The read lock guarding the database. By acquiring the read lock, the caller prevents any further data to be written
	 * to the database. Acquiring the lock must be followed by releasing the lock, preferably in a try-finally statement.
	 * @return
	 * @throws SecurityException
	 * 		if Java security is active, and the caller does not have the appropriate {@link FendoDbPermission#ADMIN admin permission}.
	 */
	// FIXME remove?
	Lock getDbLock();
	
	/**
	 * Administrative function to update the data if the internal folder list has been updated by other means
	 * than the exposed API.
	 * @throws IOException 
	 */
	void reloadDays() throws IOException;

}
