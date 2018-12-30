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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

public class FendoDbConfigurationBuilder {

	final static int DEFAULT_MAX_OPEN_FOLDERS; // 512
	final static long DEFAULT_FLUSH_PERIOD; // 10s
	final static int DEFAULT_DATA_LIFETIME_IN_DAYS; // 0 (unrestricted)
	final static int DEFAULT_MAX_DATABASE_SIZE; // 0 (unrestricted)
	final static long DEFAULT_DATA_EXPIRATION_CHECK_INTERVAL; // = 24 * 60 * 60 * 1000; // 1d
	final static long DEFAULT_RELOAD_DAYS_INTERVAL; // = 0 // disabled

	static {
		// BundleContext; avoid explicit class usage, to avoid NoClassDefFoundError when used without OSGi
		Object ctx = null;
		try {
			final org.osgi.framework.Bundle b = org.osgi.framework.FrameworkUtil.getBundle(FendoDbConfigurationBuilder.class);
			ctx = b != null ? b.getBundleContext() : null;
		} catch (NoClassDefFoundError expected) {}
		DEFAULT_MAX_OPEN_FOLDERS = getIntValue(ctx, "org.smartrplace.logging.fendo.max_open_folders", 512, 8);
		DEFAULT_FLUSH_PERIOD = getLongValue(ctx, "org.smartrplace.logging.fendo.flushperiod_ms", 10000, 0);
		DEFAULT_DATA_LIFETIME_IN_DAYS = getIntValue(ctx, "org.smartrplace.logging.fendo.limit_days", 0, 0);
		DEFAULT_MAX_DATABASE_SIZE = getIntValue(ctx, "org.smartrplace.logging.fendo.limit_size", 0, 0);
		DEFAULT_DATA_EXPIRATION_CHECK_INTERVAL = getLongValue(ctx, "org.smartrplace.logging.fendo.scanning_interval", 24 * 60 * 60 * 1000, 5 * 60 * 1000);
		DEFAULT_RELOAD_DAYS_INTERVAL = getLongValue(ctx, "org.smartrplace.logging.fendo.reloaddays_interval", 0L, 0L);
	}

	private final static int getIntValue(final Object ctx, final String property, final int defaultVal, final int minValue) {
		final String val = getProperty(ctx, property);
		if (val != null) {
			try {
				final int value = Integer.parseInt(val);
				if (value >= minValue)
					return value;
			} catch (NumberFormatException ok) {}
		}
		return defaultVal;
	}

	private final static long getLongValue(final Object ctx, final String property, final long defaultVal, final long minValue) {
		final String val = getProperty(ctx, property);
		if (val != null) {
			try {
				final long value = Long.parseLong(val);
				if (value >= minValue)
					return value;
			} catch (NumberFormatException ok) {}
		}
		return defaultVal;
	}

//	private final static long getLongValue(
//			final BundleContext ctx,
//			final String property,
//			final String propertySeconds,
//			final long defaultVal,
//			final long minValue) {
//		final String val = getProperty(ctx, property);
//		if (val != null) {
//			try {
//				final long value = Long.parseLong(val);
//				if (value >= minValue)
//					return value;
//			} catch (NumberFormatException ok) {}
//		}
//		final String valSeconds = getProperty(ctx, propertySeconds);
//		if (valSeconds != null) {
//			try {
//				final long value = Long.parseLong(valSeconds) * 1000;
//				if (value >= minValue)
//					return value;
//			} catch (NumberFormatException ok) {}
//		}
//		return defaultVal;
//	}

	private final static String getProperty(final Object ctx, final String property) {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {

			@Override
			public String run() {
				return ctx != null ? ((org.osgi.framework.BundleContext) ctx).getProperty(property) : System.getProperty(property);
			}
		});
	}


	/*
	 * limit open files in Hashmap
	 * MultiplePartlyIntervalsTest
	 * Default Linux Configuration: (should be below)
	 *
	 * host:/#> ulimit -aH [...] open files (-n) 1024 [...]
	 */
	private int maxOpenFolders = DEFAULT_MAX_OPEN_FOLDERS;

	/*
	 * configures the data flush period. The less you flush, the faster SLOTSDB
	 * will be. unset this System Property (or set to 0) to flush data directly
	 * to disk. In ms. (Note: this differs from the default slotsdb, where the period is specified in s.)
	 */
	private long flushPeriod = DEFAULT_FLUSH_PERIOD;

	/*
	 * configures how long data will at least be stored in the SLOTSDB.
	 * Set to zero or negative value to keep data indefinitely.
	 */
	private int dataLifetimeInDays = DEFAULT_DATA_LIFETIME_IN_DAYS;

	/*
	 * configures the maximum Database Size (in MB). E.g. 1024 for 1GB.
	 * Set to zero or negative value in order not to restrict the database size.
	 */
	private int maxDatabaseSize = DEFAULT_MAX_DATABASE_SIZE;


	/*
	 * Interval for scanning expired, old data, in ms. Set this to 86400000 to scan
	 * every 24 hours.
	 *
	 * Only relevant if data can expire (max lifetime or max size are set).
	 */
	private long dataExpirationCheckInterval = DEFAULT_DATA_EXPIRATION_CHECK_INTERVAL;
	
	private long reloadDaysInterval = DEFAULT_RELOAD_DAYS_INTERVAL;

	private boolean parseFoldersOnInit = false;

	private TemporalUnit unit = ChronoUnit.DAYS;

	private boolean useCompatibilityMode = false;

	private boolean readOnlyMode = false;

	private FendoDbConfigurationBuilder() {}

	/**
	 * Create a new configuration builder, initialized with sensible default values.
	 * @return
	 */
	public static FendoDbConfigurationBuilder getInstance() { return new FendoDbConfigurationBuilder(); }

	/**
	 * Copy an existing configuration
	 * @param copyConfig
	 * 		Configuration to be copied. May be null, in which case default values are used.
	 * @return
	 */
	public static FendoDbConfigurationBuilder getInstance(final FendoDbConfiguration copyConfig) {
		if (copyConfig == null)
			return getInstance();
		return new FendoDbConfigurationBuilder()
			.setDataExpirationCheckInterval(copyConfig.getDataExpirationCheckInterval())
			.setDataLifetimeInDays(copyConfig.getDataLifetimeInDays())
			.setFlushPeriod(copyConfig.getFlushPeriod())
			.setMaxDatabaseSize(copyConfig.getMaxDatabaseSize())
			.setMaxOpenFolders(copyConfig.getMaxOpenFolders())
			.setParseFoldersOnInit(copyConfig.isReadFolders())
			.setReadOnlyMode(copyConfig.isReadOnlyMode())
			.setTemporalUnit(copyConfig.getFolderCreationTimeUnit())
			.setUseCompatibilityMode(copyConfig.useCompatibilityMode())
			.setReloadDaysInterval(copyConfig.getReloadDaysInterval());
	}

	public FendoDbConfiguration build() {
		return new FendoDbConfiguration(
				readOnlyMode,
				parseFoldersOnInit,
				maxOpenFolders,
				flushPeriod,
				dataLifetimeInDays,
				maxDatabaseSize,
				dataExpirationCheckInterval,
				reloadDaysInterval,
				unit,
				useCompatibilityMode);
	}

	/**
	 * Limit number of open files.
	 * Default value is 512, or the value of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.max_open_folders"
	 */
	public FendoDbConfigurationBuilder setMaxOpenFolders(int maxOpenFolders) {
		this.maxOpenFolders = maxOpenFolders;
		return this;
	}

	/**
	 * Set the flush period in ms. Set to 0 to flush data immediately (not recommended).
	 * Default value is 10000 (10s), or the value of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.flushperiod_ms"
	 */
	public FendoDbConfigurationBuilder setFlushPeriod(long flushPeriod) {
		this.flushPeriod = flushPeriod;
		return this;
	}

	/**
	 * Set the data lifetime in days. Data older than this will be deleted. Set to 0 to keep all data.
	 * Default value is 0 (never deleted), or the value of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.limit_days"
	 */
	public FendoDbConfigurationBuilder setDataLifetimeInDays(int dataLifetimeInDays) {
		this.dataLifetimeInDays = dataLifetimeInDays;
		return this;
	}

	/**
	 * Configure the maximum database Size (in MB). E.g. 1024 for 1GB.
	 * Set to zero in order not to restrict the database size.
	 * Default value is 0, or the value of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.limit_size"
	 */
	public FendoDbConfigurationBuilder setMaxDatabaseSize(int maxDatabaseSize) {
		this.maxDatabaseSize = maxDatabaseSize;
		return this;
	}

	/**
	 * Interval for scanning expired, old data, in ms. Set this to 86400000 to scan
	 * every 24 hours.
	 *
	 * Only relevant if data can expire (max lifetime or max size are set).
	 * Default value: 1 day, or the value of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.scanning_interval"
	 */
	public FendoDbConfigurationBuilder setDataExpirationCheckInterval(long dataExpirationCheckInterval) {
		this.dataExpirationCheckInterval = dataExpirationCheckInterval;
		return this;
	};
	
	/**
	 * Trigger a periodic update of the database' internal folder list; only relevant if the 
	 * database is updated externally, i.e. bypassing the API. Default is 0, i.e. deactivated, 
	 * or the value of of the system property (or OSGi framework property) "org.smartrplace.logging.fendo.reloaddays_interval"
	 * @param reloadDaysInterval period in ms. 
	 * @return
	 */
	public FendoDbConfigurationBuilder setReloadDaysInterval(long reloadDaysInterval) {
		this.reloadDaysInterval = reloadDaysInterval;
		return this;
	}

	/**
	 * On start, try to interprete all subfolders of the database folder as persistent data? If false,
	 * 		only configurations stored in the file slotsDbStorageIDs.ser are created. Default: false.
	 */
	public FendoDbConfigurationBuilder setParseFoldersOnInit(boolean doParse) {
		this.parseFoldersOnInit = doParse;
		return this;
	};

	/**
	 * Set the basic time unit for the database. A folder will be created per
	 * interval of the unit.
	 */
	public FendoDbConfigurationBuilder setTemporalUnit(final TemporalUnit unit) {
		this.unit = Objects.requireNonNull(unit);
		if (useCompatibilityMode && !unit.equals(ChronoUnit.DAYS))
			throw new IllegalArgumentException("Temporal unit " + unit + " cannot be used in compatibility mode; requires DAYS.");
		return this;
	};

	/**
	 * Use "yyyyMMdd" labels as folder names, instead of milliseconds since epoch?
	 * Default: false.
	 * @param useCompatibilityMode
	 * @return
	 */
	public FendoDbConfigurationBuilder setUseCompatibilityMode(boolean useCompatibilityMode) {
		if (useCompatibilityMode && !unit.equals(ChronoUnit.DAYS))
			throw new IllegalArgumentException("Compatibility mode requires temporal unit DAYS.");
		this.useCompatibilityMode = useCompatibilityMode;
		return this;
	}

	/**
	 * Shall the database be opened in read only mode? Default: false.
	 * @param readOnly
	 * @return
	 */
	public FendoDbConfigurationBuilder setReadOnlyMode(boolean readOnly) {
		this.readOnlyMode = readOnly;
		return this;
	}

}
