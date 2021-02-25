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

import java.io.Serializable;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

public class FendoDbConfiguration implements Serializable {

	private static final long serialVersionUID = -8419473501302610085L;
	/*
	 * Initial delay for scheduled tasks (size watcher, data expiration, etc.)
	 */
	public static final int INITIAL_DELAY = 2 * 60 * 1000;
	private final transient boolean readOnlyMode;
	private final int maxOpenFolders;
	private final long flushPeriod;
	private final int dataLifetimeInDays;
	private final int maxDatabaseSize;
	private final long dataExpirationCheckInterval;
	private final long reloadDaysInterval;
	private final TemporalUnit unit;
	private final boolean useCompatibilityMode;
	private final boolean cacheDisabled;

	/*
	 * Minimum Size for SLOTSDB (in MB).
	 */
	public final static int MINIMUM_DATABASE_SIZE = 2;

	/*
	 * Initial delay for scheduled tasks (size watcher, data expiration, etc.)
	 */
//	public final static int INITIAL_DELAY = 100000;

	private final boolean readFolders;

	/**
	 * Use default values only. The database size is unrestricted, data lifetime as well.
	* @deprecated use {@link FendoDbConfigurationBuilder} instead
	 */
	@Deprecated
	public FendoDbConfiguration() {
		this(true, 
				FendoDbConfigurationBuilder.DEFAULT_MAX_OPEN_FOLDERS, 
				FendoDbConfigurationBuilder.DEFAULT_FLUSH_PERIOD, 
				FendoDbConfigurationBuilder.DEFAULT_DATA_LIFETIME_IN_DAYS, 
				FendoDbConfigurationBuilder.DEFAULT_MAX_DATABASE_SIZE, 
				FendoDbConfigurationBuilder.DEFAULT_DATA_EXPIRATION_CHECK_INTERVAL);
	}

	/**
	 * Specify all configuration options.
	 * @param readFolders
	 * 		On start, try to interprete all subfolders of the database folder as persistent data? If false,
	 * 		only configurations stored in the file slotsDbStorageIDs.ser are created.
	 * @param maxOpenFolders
	 * @param flushPeriodMs
	 * @param dataLifetimeDays
	 * @param maxDbSizeMB
	 * @param dataExpirationCheckItvMs
	 * @deprecated use {@link FendoDbConfigurationBuilder} instead
	 */
	@Deprecated
	public FendoDbConfiguration(boolean readFolders, int maxOpenFolders, long flushPeriodMs, int dataLifetimeDays, int maxDbSizeMB, long dataExpirationCheckItvMs) {
		this(false, readFolders, maxOpenFolders, flushPeriodMs, dataLifetimeDays, maxDbSizeMB, dataExpirationCheckItvMs, 0, ChronoUnit.DAYS, false, false);
	}

	FendoDbConfiguration(
			boolean readOnlyMode,
			boolean readFolders, 
			int maxOpenFolders, 
			long flushPeriodMs, 
			int dataLifetimeDays, 
			int maxDbSizeMB, 
			long dataExpirationCheckItvMs,
			long reloadDaysInterval,
			TemporalUnit unit,
			boolean useCompatibilityMode,
			boolean cacheDisabled) {
		this.readOnlyMode = readOnlyMode;
		if (maxOpenFolders <= 0)
			throw new IllegalArgumentException("MaxOpenFolders must be a positive number");
		this.readFolders = readFolders;
		this.maxOpenFolders = maxOpenFolders;
		this.flushPeriod = readOnlyMode ? 0 : flushPeriodMs;
		this.dataLifetimeInDays = readOnlyMode ? 0 : dataLifetimeDays;
		this.maxDatabaseSize = readOnlyMode ? 0 : maxDbSizeMB;
		if (readOnlyMode)
			this.dataExpirationCheckInterval = 0;
		else if (dataExpirationCheckItvMs > 0 && dataExpirationCheckItvMs <= 5  * 60 * 1000)
			this.dataExpirationCheckInterval = 5 * 50 * 1000;
		else
			this.dataExpirationCheckInterval = dataExpirationCheckItvMs;
		this.unit = Objects.requireNonNull(unit);
		this.useCompatibilityMode = useCompatibilityMode;
		this.reloadDaysInterval = reloadDaysInterval;
		if (useCompatibilityMode && !unit.equals(ChronoUnit.DAYS))
			throw new IllegalArgumentException("Temporal unit " + unit + " cannot be used in compatibility mode; requires DAYS.");
		this.cacheDisabled = cacheDisabled;
	}
	

	public int getMaxOpenFolders() {
		return maxOpenFolders;
	}

	/**
	 * Flush period in ms.
	 * @return
	 */
	public long getFlushPeriod() {
		return flushPeriod;
	}

	public int getDataLifetimeInDays() {
		return dataLifetimeInDays;
	}

	/**
	 * Database size in MB.
	 * @return
	 */
	public int getMaxDatabaseSize() {
		return maxDatabaseSize;
	}

	/**
	 * In ms.
	 * @return
	 */
	public long getDataExpirationCheckInterval() {
		return dataExpirationCheckInterval;
	}
	
	/**
	 * In ms
	 * @return
	 */
	public long getReloadDaysInterval() {
		return reloadDaysInterval;
	}

	public boolean isReadFolders() {
		return readFolders;
	}
	
	public TemporalUnit getFolderCreationTimeUnit() {
		return unit;
	}
	
	/**
	 * Use "yyyyMMdd" labels as folder names, instead of milliseconds since epoch?
	 * @return
	 */
	public boolean useCompatibilityMode() {
		return useCompatibilityMode;
	}
	
	public boolean isReadOnlyMode() {
		return readOnlyMode;
	}
	
	public boolean isCacheDisabled() {
		return cacheDisabled;
	}
	
	@Override
	public String toString() {
		return "SlotsDB configuration; time unit: " + unit + ", data lifetime " + dataLifetimeInDays + " days, flush period: " + flushPeriod
				+ " max data size: " + maxDatabaseSize + " MB, max open folders: " + maxOpenFolders + ", compat mode: " + useCompatibilityMode;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(unit, flushPeriod, useCompatibilityMode, readOnlyMode);
	}
	
	@Override
	public boolean equals(final Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof FendoDbConfiguration))
			return false;
		final FendoDbConfiguration other = (FendoDbConfiguration) obj;
		return this.unit.equals(other.unit)
			&& this.readOnlyMode == other.readOnlyMode
			&& this.readFolders == other.readFolders
			&& this.useCompatibilityMode == other.useCompatibilityMode
			&& this.dataExpirationCheckInterval == other.dataExpirationCheckInterval
			&& this.dataLifetimeInDays == other.dataLifetimeInDays
			&& this.maxDatabaseSize == other.maxDatabaseSize
			&& this.maxOpenFolders == other.maxOpenFolders;
	}
	
	
}
