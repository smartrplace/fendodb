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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;

class GogoCommands {

	private final static DateTimeFormatter formatter = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd")
			.optionalStart()
				.appendPattern("'T'HH")
				.optionalStart()
					.appendPattern(":mm")
					.optionalStart()
						.appendPattern(":ss")
					.optionalEnd()
				.optionalEnd()
			.optionalEnd()
			.toFormatter(Locale.ENGLISH);
	private final static ZoneId zone = ZoneId.of("Z");
	private final FendoDbFactory factory;

	GogoCommands(FendoDbFactory factory) {
		this.factory = factory;
	}

	@Descriptor("Get all FendoDB instances")
	public Collection<DataRecorderReference> getFendoDbs() {
		return factory.getAllInstances().values();
	}

	@Descriptor("Create FendoDB instance")
	public CloseableDataRecorder openFendoDb(@Descriptor("Path to database root") final String path) throws IOException {
		final Path path2 = Paths.get(path);
		if (!Files.exists(path2)) {
			System.out.println("Path " + path + " does not exist");
			return null;
		}
		return factory.getInstance(path2);
	}

	@Descriptor("Get the configuration for a specific FendoDB instance")
	public FendoDbConfiguration getFendoDbConfig(
		@Descriptor("The path for the slotsDb instance") final String path) throws IOException {
		try (final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(path))) {
			if (instance == null) {
				System.out.println("SlotsDb instance for path " + path + " not found");
				return null;
			}
			return instance.getConfiguration();
		}
	}

	@Descriptor("Get a time series")
	public FendoTimeSeries getFendoDbTimeSeries(
			@Descriptor("The path for the FendoDb instance") final String path,
			@Descriptor("The timeseries id") final String id) throws IOException {
		try (final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(path))) {
			if (instance == null) {
				System.out.println("FendoDb instance for path " + path + " not found");
				return null;
			}
			return instance.getRecordedDataStorage(id);
		}
	}
	
	@Descriptor("Get all time series")
	public Collection<FendoTimeSeries> getAllFendoDbTimeSeries(
			@Descriptor("The path for the FendoDb instance") final String path) throws IOException {
		try (final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(path))) {
			if (instance == null) {
				System.out.println("FendoDb instance for path " + path + " not found");
				return null;
			}
			return instance.getAllTimeSeries();
		}
	}

	@Descriptor("Search for time series")
	public List<FendoTimeSeries> findTimeSeries(
			@Descriptor("Filter out empty time series?")
			@Parameter(names= {"-e", "--empty"} , absentValue="false", presentValue="true") final boolean emptyFilter,
			@Descriptor("Tags (property keys) to filter for. A comma-separated list of tags")
			@Parameter(names= {"-t", "--tags"}, absentValue="") final String tags,
			@Descriptor("Properties to filter for. A comma-separated list of key=value pairs")
			@Parameter(names= {"-p", "--props"}, absentValue="") final String properties,
			@Descriptor("The path for the slotsDb instance") final String path) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("FendoDb instance for path " + path + " not found");
				return null;
			}
			final SearchFilterBuilder builder = SearchFilterBuilder.getInstance();
			if (!properties.isEmpty()) {
				builder.filterByProperties(Arrays.stream(properties.split(","))
					.map(str -> str.split("="))
					.filter(arr -> arr.length == 2 && !arr[0].trim().isEmpty())
					.collect(Collectors.toMap(arr -> arr[0].trim(), arr -> arr[1].trim())), true);
			}
			if (!tags.isEmpty()) {
				final List<String> t = Arrays.stream(tags.split(","))
					.map(tag -> tag.trim())
					.filter(tag -> !tag.isEmpty())
					.collect(Collectors.toList());
				if (!t.isEmpty()) {
					final String[] arr = new String[t.size()];
					t.toArray(arr);
					builder.filterByTags(arr);
				}
			}
			if (emptyFilter)
				builder.requireNonEmpty();
			return slots.findTimeSeries(builder.build());
		}
	}

	@Descriptor("Copy the database to another location")
	public DataRecorderReference copyFendoDb(
			@Descriptor("Start time, either in milliseconds since epoch, or a date string in the format 'yyyy-MM-dd[THH[:mm[:ss]]]'. Default value is Long.MIN_VALUE.")
			@Parameter(names= {"-s","-start"}, absentValue="") final String startTime,
			@Descriptor("End time, either in milliseconds since epoch, or a date string in the format 'yyyy-MM-dd[THH[:mm[:ss]]]'. Default value is Long.MAX_VALUE.")
			@Parameter(names= {"-e","-end"}, absentValue="") final String endTime,
			@Descriptor("Open database in read only mode?")
			@Parameter(names= {"-r", "--readonly"}, absentValue="false", presentValue="true") final String readOnly,
			@Descriptor("Basic time unit for database folders, such as 'DAYS', 'HOURS', 'MONTHS', etc.")
			@Parameter(names= {"-u", "--unit"}, absentValue="") final String unit,
			@Descriptor("Use compatibility mode for database folder names? Implies time unit 'DAYS'.")
			@Parameter(names= {"-c", "--compatMode"}, absentValue="false", presentValue="true") final String compatibilityMode,
			@Descriptor("Database flush period in ms. 0 means flush immediately.")
			@Parameter(names= {"-f", "--flushperiodms"}, absentValue="") final String flushPeriodMs,
			@Descriptor("Maximum number of open folders.")
			@Parameter(names= {"-o", "--openfolders"}, absentValue="") final String openFolders,
			@Descriptor("Maximum database size in MB. 0 means unrestricted.")
			@Parameter(names= {"-sz", "--sizemb"}, absentValue="") final String maxSizeMb,
			@Descriptor("Maximum data lifetime in days. 0 means unrestricted.")
			@Parameter(names= {"-l", "--lifetimedays"}, absentValue="") final String maxLifetimeDays,
			@Descriptor("Data expiration check interval in ms.")
			@Parameter(names= {"-exp", "--expirationms"}, absentValue="") final String dataExpirationCheckIntervalMs,
			@Descriptor("Path to the original database")
			final String originalPath,
			@Descriptor("New path")
			final String newPath) throws IOException {
		final Path originalPath1 = Paths.get(originalPath);
		final Path newPath1 = Paths.get(newPath);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(originalPath1)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + originalPath);
				return null;
			}
			final FendoDbConfigurationBuilder cfgBuilder = FendoDbConfigurationBuilder.getInstance(slots.getConfiguration());
			final FendoDbConfiguration cfg = getConfig(cfgBuilder, readOnly, unit, compatibilityMode, flushPeriodMs, openFolders, maxSizeMb,
					maxLifetimeDays, dataExpirationCheckIntervalMs);
			if (cfg == null)
				return null;
			final long start = parseTimeString(startTime, Long.MIN_VALUE);
			final long end = parseTimeString(endTime, Long.MAX_VALUE);
			return slots.copy(newPath1, cfgBuilder.build(), start, end);
		}
	}

	@Descriptor("Update the database configuration")
	public DataRecorderReference updateConfig(
			@Descriptor("Open database in read only mode?")
			@Parameter(names= {"-r", "--readonly"}, absentValue="false", presentValue="true") final String readOnly,
			@Descriptor("Basic time unit for database folders, such as 'DAYS', 'HOURS', 'MONTHS', etc.")
			@Parameter(names= {"-u", "--unit"}, absentValue="") final String unit,
			@Descriptor("Use compatibility mode for database folder names? Implies time unit 'DAYS'.")
			@Parameter(names= {"-c", "--compatMode"}, absentValue="false", presentValue="true") final String compatibilityMode,
			@Descriptor("Database flush period in ms. 0 means flush immediately.")
			@Parameter(names= {"-f", "--flushperiodms"}, absentValue="") final String flushPeriodMs,
			@Descriptor("Maximum number of open folders.")
			@Parameter(names= {"-o", "--openfolders"}, absentValue="") final String openFolders,
			@Descriptor("Maximum database size in MB. 0 means unrestricted.")
			@Parameter(names= {"-sz", "--sizemb"}, absentValue="") final String maxSizeMb,
			@Descriptor("Maximum data lifetime in days. 0 means unrestricted.")
			@Parameter(names= {"-l", "--lifetimedays"}, absentValue="") final String maxLifetimeDays,
			@Descriptor("Data expiration check interval in ms.")
			@Parameter(names= {"-exp", "--expirationms"}, absentValue="") final String dataExpirationCheckIntervalMs,
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws Exception {
		final Path originalPath1 = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(originalPath1)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return null;
			}
			final FendoDbConfigurationBuilder cfgBuilder = FendoDbConfigurationBuilder.getInstance(slots.getConfiguration());
			final FendoDbConfiguration cfg = getConfig(cfgBuilder, readOnly, unit, compatibilityMode, flushPeriodMs, openFolders, maxSizeMb,
					maxLifetimeDays, dataExpirationCheckIntervalMs);
			if (cfg == null)
				return null;
			return slots.updateConfiguration(cfg);
		}
	}

	@Descriptor("Return all property values present in the database")
	public Map<String, Collection<String>> getAllProperties(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return null;
			}
			return slots.getAllProperties();
		}
	}

	@Descriptor("Return all property values present in the database, for a specific key")
	public Collection<String> getAllPropertyValues(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("Property key")
			final String key) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return null;
			}
			return slots.getAllPropertyValues(key);
		}
	}

	@Descriptor("Get all properties for a specific time series")
	public Map<String, List<String>> getPropertiesById (
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("Timeseries id")
			final String id) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return null;
			}
			final List<FendoTimeSeries> list =slots.findTimeSeries(SearchFilterBuilder.getInstance().filterById(id, true).build());
			if (list.isEmpty()) {
				System.out.println("No time series found with id " + id);
				return null;
			}
			return list.get(0).getProperties();
		}
	}

	@Descriptor("Set a property for a specific time series")
	public boolean setProperty (
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("Timeseries id")
			final String id,
			@Descriptor("Property key")
			final String key,
			@Descriptor("Property value")
			final String value) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final List<FendoTimeSeries> list =slots.findTimeSeries(SearchFilterBuilder.getInstance().filterById(id, true).build());
			if (list.isEmpty()) {
				System.out.println("No time series found with id " + id);
				return false;
			}
			list.get(0).setProperty(key, value);
			return true;
		}
	}

	@Descriptor("Remove a property/tag from a specific time series")
	public boolean removeProperty (
			@Descriptor("Property value (optional). If this a present, only the specified (key, value)-pair "
					+ "will be removed from the properties list")
			@Parameter(names= {"-v", "--value"}, absentValue="") final String value,
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("Timeseries id")
			final String id,
			@Descriptor("Property key")
			final String key) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final List<FendoTimeSeries> list = slots.findTimeSeries(SearchFilterBuilder.getInstance().filterById(id, true).build());
			if (list.isEmpty()) {
				System.out.println("No time series found with id " + id);
				return false;
			}
			if (value.isEmpty())
				return list.get(0).removeProperty(key);
			else
				return list.get(0).removeProperty(key, value);
		}
	}

	@Descriptor("Add a property to a specific time series")
	public boolean addProperty (
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("Timeseries id")
			final String id,
			@Descriptor("Property key")
			final String key,
			@Descriptor("Property value")
			final String value) throws IOException {
		final Path p = Paths.get(path);
		try (final CloseableDataRecorder slots = factory.getExistingInstance(p)) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final List<FendoTimeSeries> list =slots.findTimeSeries(SearchFilterBuilder.getInstance().filterById(id, true).build());
			if (list.isEmpty()) {
				System.out.println("No time series found with id " + id);
				return false;
			}
			list.get(0).addProperty(key, value);
			return true;
		}
	}

	@Descriptor("Delete all data in a given FendoDb instance before some timestamp")
	public boolean deleteDataBefore(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("The timestamp, either in milliseconds since epoch, or a date string in the format 'yyyy-MM-dd[THH[:mm[:ss]]]'.")
			final String time
			) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final long t = parseTimeString(time, Long.MIN_VALUE);
			if (t == Long.MIN_VALUE) {

				System.out.println("Illegal time: " + time);
				return false;
			}
			return slots.deleteDataBefore(Instant.ofEpochMilli(t));
		}
	}

	@Descriptor("Delete all data in a given FendoDb instance after some timestamp")
	public boolean deleteDataAfter(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("The timestamp, either in milliseconds since epoch, or a date string in the format 'yyyy-MM-dd[THH[:mm[:ss]]]'.")
			final String time
			) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final long t = parseTimeString(time, Long.MIN_VALUE);
			if (t == Long.MIN_VALUE) {
				System.out.println("Illegal time: " + time);
				return false;
			}
			return slots.deleteDataAfter(Instant.ofEpochMilli(t));
		}
	}

	@Descriptor("Delete all data in a given FendoDb instance before some timestamp")
	public boolean deleteDataOlderThan(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path,
			@Descriptor("The duration.")
			final int amount,
			@Descriptor("The time unit, see ChronoUnit class. Examples: 'MINUTES', 'HOURS', 'DAYS', ...")
			final String unit
			) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			if (amount <= 0) {
				System.out.println("Time amount must be positive, got " +amount );
				return false;
			}
			final ChronoUnit cunit;
			try {
				cunit = ChronoUnit.valueOf(unit.toUpperCase());
			} catch (IllegalArgumentException e) {
				System.out.println("Time unit " + unit + " not found.");
				return false;
			}
			final Duration duration = Duration.of(amount, cunit);
			return slots.deleteDataOlderThan(duration);
		}
	}

	@Descriptor("Get the number of references held to the specified database. "
			+ "Note that one instance will be opened by this method call, but it is not counted here.")
	public int getReferenceCount(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return -1;
			}
			final SlotsDb impl = getImplementation(slots);
			return impl.getReferenceCount() - 1;
		}
	}

	@Descriptor("Close a FendoDb instance. Note that this may lead to problems in applications using the database.")
	public boolean closeFendoDb(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		try (final CloseableDataRecorder slots = factory.getExistingInstance(Paths.get(path))) {
			if (slots == null) {
				System.out.println("No database found under the specified path " + path);
				return false;
			}
			final SlotsDb impl = getImplementation(slots);
			impl.close();
			return true;
		}
	}

	@Descriptor("Check if a FendoDb instance exists.")
	public boolean fendoDbExists(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		return factory.databaseExists(Paths.get(path));
	}

	@Descriptor("Check if a FendoDb instance is currently in use.")
	public boolean isFendoDbActive(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		return ((SlotsDbFactoryImpl) factory).databaseIsActive(Paths.get(path));
	}
	
	@Descriptor("Refresh a database that has been updated by non-API means")
	public void reloadDays(
			@Descriptor("Database path, relative to rundir or absolute")
			final String path) throws IOException {
		try (final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(path))) {
			if (instance == null)
				System.out.println("Not found");
			else
				instance.reloadDays();
		}
	}

	private final SlotsDb getImplementation(final CloseableDataRecorder recorder) {
		if (recorder instanceof SlotsDbProxy)
			return ((SlotsDbProxy) recorder).master;
		else if (recorder instanceof SlotsDb)
			return (SlotsDb) recorder;
		else
			throw new IllegalStateException("Unexpected data recorder type " + recorder.getClass().getName());
	}

	private static FendoDbConfiguration getConfig(
			final FendoDbConfigurationBuilder builder,
			final String readOnly,
			final String unit,
			final String compatibilityMode,
			final String flushPeriodMs,
			final String openFolders,
			final String maxSizeMb,
			final String maxLifetimeDays,
			final String dataExpirationCheckIntervalMs) {
		final TemporalUnit newUnit = getUnit(unit);
		builder.setUseCompatibilityMode(Boolean.parseBoolean(compatibilityMode));
		if (newUnit != null)
			builder.setTemporalUnit(newUnit);
		if (!readOnly.isEmpty())
			builder.setReadOnlyMode(Boolean.parseBoolean(readOnly));
		if (!flushPeriodMs.isEmpty()) {
			try {
				final long flushPeriod = Long.parseLong(flushPeriodMs);
				if (flushPeriod < 0) {
					System.out.println("Negative flush period not allowed");
					return null;
				}
				builder.setFlushPeriod(flushPeriod);
			} catch (NumberFormatException e) {
				System.out.println("Not a valid number: " + flushPeriodMs);
				return null;
			}
		}
		if (!openFolders.isEmpty()) {
			try {
				final int open = Integer.parseInt(openFolders);
				if (open < 0) {
					System.out.println("Negative number of open folders not allowed");
					return null;
				}
				builder.setMaxOpenFolders(open);
			} catch (NumberFormatException e) {
				System.out.println("Not a valid number: " + openFolders);
				return null;
			}
		}
		if (!maxSizeMb.isEmpty()) {
			try {
				final int mb = Integer.parseInt(maxSizeMb);
				if (mb < 0) {
					System.out.println("Negative database size not allowed");
					return null;
				}
				builder.setMaxDatabaseSize(mb);
			} catch (NumberFormatException e) {
				System.out.println("Not a valid number: " + maxSizeMb);
				return null;
			}
		}
		if (!maxLifetimeDays.isEmpty()) {
			try {
				final int days = Integer.parseInt(maxLifetimeDays);
				if (days < 0) {
					System.out.println("Negative data lifetime not allowed");
					return null;
				}
				builder.setDataLifetimeInDays(days);
			} catch (NumberFormatException e) {
				System.out.println("Not a valid number: " + maxLifetimeDays);
				return null;
			}
		}
		if (!dataExpirationCheckIntervalMs.isEmpty()) {
			try {
				final long interval = Long.parseLong(dataExpirationCheckIntervalMs);
				if (interval < 0) {
					System.out.println("Negative data expiration check interval not alowed");
					return null;
				}
				builder.setDataExpirationCheckInterval(interval);
			} catch (NumberFormatException e) {
				System.out.println("Not a valid number: " + dataExpirationCheckIntervalMs);
				return null;
			}
		}
		return builder.build();
	}

	private final static long parseTimeString(final String time, final long defaulValue) {
		if (time == null || time.isEmpty())
			return defaulValue;
		try {
			return Long.parseLong(time);
		} catch (NumberFormatException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.from(formatter.parse(time)), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		try {
			return ZonedDateTime.of(LocalDateTime.of(LocalDate.from(formatter.parse(time)), LocalTime.MIN), zone).toInstant().toEpochMilli();
		} catch (DateTimeException e) {}
		return defaulValue;
	}

	private final static TemporalUnit getUnit(final String string) {
		return Arrays.stream(ChronoUnit.values())
			.filter(unit -> unit.toString().equalsIgnoreCase(string))
			.findAny().orElse(null);
	}

}
