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
package org.smartrplace.logging.fendodb.influx;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.felix.service.command.Descriptor;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.BoundParameterQuery.QueryBuilder;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIterator;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIteratorBuilder;
import org.ogema.tools.timeseries.iterator.api.SampledValueDataPoint;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tagging.api.LogDataTaggingConstants;

@Component(
		service = Runnable.class,
		configurationPid = InfluxConfig.PID,
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		// default properties, can be overwritten via config properties
		property = {
				"osgi.command.scope=fendodb",
				"osgi.command.function=influxExport",
				"org.smartrplace.tools.housekeeping.Period:Long=6",
				"org.smartrplace.tools.housekeeping.Delay:Long=6",
				"org.smartrplace.tools.housekeeping.Unit=HOURS",
				"service.factoryPid=" + InfluxConfig.PID
		}
)
@Designate(ocd=InfluxConfig.class)
// see https://github.com/influxdata/influxdb-java
public class InfluxExport implements Runnable {
	
	/**
	 * A date-time formatter
	 */
	private final static DateTimeFormatter DEFAULT_FORMATTER_PARSE_WITH_ZONE = new DateTimeFormatterBuilder()
			.appendPattern("yyyy")
			.optionalStart()
				.appendPattern("-MM")
				.optionalStart()
					.appendPattern("-dd")
					.optionalStart()
						.appendPattern("'T'HH")
						.optionalStart()
							.appendPattern(":mm")
							.optionalStart()
								.appendPattern(":ss")
							.optionalEnd()
						.optionalEnd()
					.optionalEnd()
				.optionalEnd()
			.optionalEnd()
			.optionalStart()
				.appendZoneOrOffsetId()
			.optionalEnd()
			.toFormatter(Locale.ENGLISH);
	
	/**
	 * A date-time formatter without time zone
	 */
	private final static DateTimeFormatter DEFAULT_FORMATTER_PARSE_NO_ZONE = new DateTimeFormatterBuilder()
			.appendPattern("yyyy")
			.optionalStart()
				.appendPattern("-MM")
				.optionalStart()
					.appendPattern("-dd")
					.optionalStart()
						.appendPattern("'T'HH")
						.optionalStart()
							.appendPattern(":mm")
							.optionalStart()
								.appendPattern(":ss")
							.optionalEnd()
						.optionalEnd()
					.optionalEnd()
				.optionalEnd()
			.optionalEnd()
			.toFormatter(Locale.ENGLISH);
	
	private static final String UNKNOWN_FIELD_TYPE = "value";
	private static final List<String> measurementTypes = Arrays.asList(
			   "temperature",
			   "humidity",
			   "power",
			   "current",
			   "voltage",
			   "energy",
			   "irradiation",
			   "motion"
		);
	
	@Reference
	private ComponentServiceObjects<FendoDbFactory> fendoFactory;
	
	private InfluxDB influx;
	private InfluxConfig config;
	private long startTime = Long.MIN_VALUE;
	private long endTime = Long.MAX_VALUE;
	private final CompletableFuture<InfluxDB> future = new CompletableFuture<>();
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@SuppressWarnings("unchecked")
	@Descriptor("For manually trigger exporting timeseries to InfluxDB")
	public CompletionStage<Long> influxExport(
			@Descriptor("Directory path where the data is stored (e.g. _16099/slotsdb") String referencePath,
			@Descriptor("Starttime of the wanted time interval: Format YYYY-MM-ddTHH:mm:ss (or just YYYY-MM-dd)") final String startInterval,
			@Descriptor("Endtime of the wanted time interval: Format YYYY-MM-ddTHH:mm:ss (or just YYYY-MM-dd)") final String endInterval) {
		System.out.println("Uploading FendoDb data to InfluxDb for " + referencePath);
		final ExecutorService exec = Executors.newSingleThreadExecutor();
		final CompletableFuture<Long> result = CompletableFuture.supplyAsync(() -> {
			final long startT = parseTimestamp(startInterval);
			final long endT = parseTimestamp(endInterval);
			final String path = config.fendodb();
			final FendoDbFactory factory = fendoFactory.getService();
			try {
				final String prefix = path.contains("*") ? path.substring(0, path.indexOf('*')) : path;
				return factory.getAllInstances().entrySet().stream()
					.filter(entry -> entry.getKey().toString().replace('\\', '/').startsWith(prefix))
					.map(Map.Entry::getValue)
					.filter(reference -> reference.getPath().endsWith(referencePath)) // referencePath is something like "_16006/slotsdb"
					.mapToLong(reference -> upload(reference, startT, endT))
					.sum();
			} finally {
				fendoFactory.ungetService(factory);
			}
		}, exec);
		result.whenCompleteAsync((r,exc) -> exec.shutdown());
		result.whenCompleteAsync((r,e) -> {
			if (e != null) {
				System.out.println("Failed to upload to InfluxDB");
				e.printStackTrace(System.out);
			} else {
				System.out.println("Upload to InfluxDB completed for " + referencePath+ ", nr values: " +  r);
			}
		});
		// we cannot return the CompletableFuture object, because then gogo will call its #get method
		// when trying to display the object
		return (CompletionStage<Long>) Proxy.newProxyInstance(CompletionStage.class.getClassLoader(),
                new Class<?>[] { CompletionStage.class },
                (proxy, method, args) -> method.invoke(result, args));

	}
	
	@Activate
	protected void activate(InfluxConfig config) throws InterruptedException {
		this.config = config;
		if (!config.startTime().isEmpty())
			startTime = parseTimestamp(config.startTime());
		if (!config.endTime().isEmpty())
			endTime = parseTimestamp(config.endTime());
		this.influx  = InfluxDBFactory.connect(config.url(), config.user(), config.pw());
		influx.setDatabase(config.influxdb());
		future.thenAcceptAsync(this::createDb);
		if (!ping()) {
			logger.info("InfluxDb is not reachable");
			return;
		}
	}

	@Override
	public void run() {
		logger.info("Uploading FendoDb data to InfluxDb");
		if (!ping()) {
			logger.info("InfluxDb is not reachable");
			return;
		}
		final String path = config.fendodb();
		final FendoDbFactory factory = fendoFactory.getService();
		try {
			if (path.contains("*")) {
				final String prefix = path.substring(0, path.indexOf('*'));
				factory.getAllInstances().entrySet().stream()
					.filter(entry -> entry.getKey().toString().replace('\\', '/').startsWith(prefix))
					.map(Map.Entry::getValue)
					.forEach(reference -> upload(reference, null, null));
			} else {
				try {
					final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(path));
					if (instance == null) {
						logger.warn("Configured FendoDb instance does not exist: {}", path);
						return;
					}
					transfer(instance, null, null, null);
				} catch (IOException | InvalidPathException e) {
					logger.warn("Failed to transfer FendoDb data", e);
				}
			}
		} finally {
			fendoFactory.ungetService(factory);
		}
	}
	
	private long upload(DataRecorderReference reference, final Long startT, final Long endT) {
		
		try {
			final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance(reference.getConfiguration())
					.setReadOnlyMode(true)
					.build();
			return transfer(reference.getDataRecorder(config), getMeasurementId(reference), startT, endT);
		} catch (Exception e) {
			logger.warn("Failed to transfer FendoDb data", e);
			return 0;
		}	
	}
	
	private void createDb(final InfluxDB influx) {
		final Query query = QueryBuilder.newQuery("CREATE DATABASE \"" + config.influxdb() + "\"")
				.forDatabase(config.influxdb())
				.create();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<List<Result>> results = new AtomicReference<>();
		final AtomicReference<Throwable> error = new AtomicReference<>();
		influx.query(query, result -> {
				results.set(result == null ? null : result.getResults());
				latch.countDown();
			}, exc -> {
				error.set(exc);
				latch.countDown();
			});
		try {
			if (!latch.await(60, TimeUnit.SECONDS)) {
				logger.info("Database creation request timed out");
			}
			else if (error.get() != null) {
				logger.info("Database creation request failed: {}", error.get());
			} else {
				logger.info("Database created: {}", results.get());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	private final boolean ping() {
		boolean success = false;
		try {
			success = influx.ping().isGood();
			if (success && !future.isDone())
				future.complete(influx);
		} catch (Exception expected) {} // XXX exceptions on Influx connector lib not documented
		return success;
	}
	
	// transfer delivers the size of all datapoints uploaded for one instance (e.g. gw)
	private long transfer(final CloseableDataRecorder fendo, final String instance, final Long startT, final Long endT) {
		// fendo.getAllTimeResources = 
		final List<FendoTimeSeries> ts = fendo.getAllTimeSeries();
//		final Map<String, List<FendoTimeSeries>> tsByTags = ts.stream()
//			.collect(Collectors.groupingBy(InfluxExport::getTagsAsString));
//		return tsByTags.values().stream().mapToLong(tag -> transfer(tag, fendo.getPath().toString().replace('\\', '/'))) // TODO do we need to replace any characters?
//			.sum();
		long sum = 0;
		for (FendoTimeSeries t : ts) {
			if (Thread.interrupted()) {
				try {
					Thread.currentThread().interrupt();
				} catch (SecurityException ignore) {}
				break;
			}
			sum += transfer(Collections.singletonList(t), instance, startT, endT);
		}
		return sum;
	}	
	
	// transfer delivers the size of all datapoints uploaded for one resource of one instance (e.g. gw)
	private long transfer(final List<FendoTimeSeries> timeSeries, final String instance, final Long startT, final Long endT) {
		
		final String measId0 = config.measurementid();
		final String measurementId = instance != null && measId0.isEmpty() ? instance : instance != null ? measId0 + "_" + instance : measId0;
		
		long max = timeSeries.stream()
			.mapToLong(ts -> getLastTimestamp(ts, measurementId))
			.max().orElse(Long.MIN_VALUE);
		
		final BatchPoints.Builder builder = BatchPoints.database(config.influxdb())
				.precision(TimeUnit.MILLISECONDS);
		// Influx DB supports a single tag value only
		
		timeSeries.iterator().next().getProperties().entrySet().stream()
			.forEach(entry -> builder.tag(entry.getKey(), entry.getValue().iterator().next()));

		final List<String> fieldIds = timeSeries.stream()
				.map(FendoTimeSeries::getProperties)
				.map(InfluxExport::getFieldNameFromProperties)
				.collect(Collectors.toList());
		long size = 0;
		int failureCnt = 0;
		try {
			final Field timeField = Point.class.getDeclaredField("time");
			timeField.setAccessible(true);
			
			if(startT != null && endT != null) {
				startTime = startT.longValue();
				endTime = endT.longValue();
			}
			
			while (true) {
				if (Thread.interrupted()) {
					try {
						Thread.currentThread().interrupt();
					} catch (SecurityException ignore) {}
					break;
				}
				if (max == Long.MAX_VALUE)
					break;
				final long start = Math.max(max + 1, startTime);
				if (start > endTime)
					break;
				final List<Point> points;
				if (timeSeries.size() == 1)
					points = getPoints(timeSeries.get(0).getPath(), timeSeries.get(0).iterator(start, endTime), measurementId, fieldIds.get(0));
				else {
					final MultiTimeSeriesIterator it = 
							MultiTimeSeriesIteratorBuilder.newBuilder(timeSeries.stream().map(t -> t.iterator(start, endTime)).collect(Collectors.toList())).build();
					points = getPoints(timeSeries.stream().map(FendoTimeSeries::getPath).collect(Collectors.toList()), it, measurementId, fieldIds);
				}
				if (points.isEmpty())
					break;
				final Point[] arr = new Point[points.size()];
				points.toArray(arr);
				try {
					final BatchPoints batchPoints = builder
						.points(arr)
						.build();
					influx.write(batchPoints);
				} catch (InfluxDBException exc) { // typically the
					if (failureCnt++ == 0) // retry once
						continue;
					logger.error("Failed to write all data of timeseries {} from {} after the second try. Amount of data points: {}", timeSeries.get(0).getPath(), instance, points.size(), exc);
					break;
				}
				failureCnt = 0;
				size += arr.length; 
				// max = arr[arr.length-1].time
				max = ((Long) timeField.get(arr[arr.length-1])).longValue();
			}
			
			logger.debug("Exported {} datapoints for timeseries {} from {} to {}", size, timeSeries.get(0).getPath(), 
					startT == null ? null : new Date(startT), endT == null ? null : new Date(endT));
			
		} catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
		return size;
	}
	
	private long getLastTimestamp(final FendoTimeSeries ts, final String measurementId) {
		final String path =  ts.getPath();
		final String fieldId = getFieldNameFromProperties(ts.getProperties());
		final String query = "SELECT last(\"" + fieldId + "\") FROM \"" + measurementId + "\" WHERE \"path\"='" + path +  "'";
		List<Result> results;
		try {
			results = sendQuery(QueryBuilder.newQuery(query).forDatabase(config.influxdb()).create());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Long.MAX_VALUE;
		}
		if (results == null || results.isEmpty())
			return Long.MIN_VALUE;
		final List<Series> seriesList = results.iterator().next().getSeries();
		if (seriesList == null || seriesList.isEmpty())
			return Long.MIN_VALUE;
		final Series series = seriesList.iterator().next();
		int i = 0;
		final List<String> cols = series.getColumns();
		if (cols == null || cols.isEmpty())
			return Long.MIN_VALUE;
		while (i < cols.size()) {
			if ("time".equals(cols.get(i)))
				break;
			i++;
		}
		if (i == cols.size())
			return Long.MIN_VALUE;
		final String time = (String) series.getValues().iterator().next().get(i);

		return ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(time)).toInstant().toEpochMilli();
	}
	
	private final List<Result> sendQuery(final Query query) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<List<Result>> results = new AtomicReference<>();
		final AtomicReference<Throwable> error = new AtomicReference<>();
		influx.setDatabase(config.influxdb());
		influx.query(query, result -> {
				results.set(result == null ? null : result.getResults());
				latch.countDown();
			}, exc -> {
				error.set(exc);
				latch.countDown();
			});
		if (!latch.await(30, TimeUnit.SECONDS)) {
			logger.warn("Request timed out");
			return null;
		}
		if (error.get() != null) {
			logger.warn("Request failed",error.get());
			return null;
		}
		return results.get();
	}
	
	private static String getFieldNameFromProperties(final Map<String, List<String>> props) {
		final List<String> deviceTypes = props.get(LogDataTaggingConstants.DEVICE_TYPE_SPECIFIC);
		if (deviceTypes == null)
			return UNKNOWN_FIELD_TYPE;
		return measurementTypes.stream()
			.filter(type -> containsIgnoreCase(deviceTypes, type))
			.findFirst().orElse(UNKNOWN_FIELD_TYPE);
	}
	
	private static boolean containsIgnoreCase(final List<String> list, final String value) {
		return list.stream().filter(str -> str.toLowerCase().contains(value)).findAny().isPresent();
	}
	
	private static List<Point> getPoints(final String path, final Iterator<SampledValue> it, final String measurementId, final String fieldId) {
		final List<Point> points = new ArrayList<>();
		int cnt = 0;
		while(it.hasNext() && cnt <= 500) {
			final SampledValue sv = it.next();
			final Point point = Point.measurement(measurementId)
					.time(sv.getTimestamp(), TimeUnit.MILLISECONDS)
					.addField(fieldId, sv.getValue().getDoubleValue())
					.tag("path", path)
					.build();
			points.add(point);
			cnt++;
		}
		return points;
	}
	
	private static List<Point> getPoints(final List<String> paths, final MultiTimeSeriesIterator it, final String measurementId, final List<String> fieldIds) {
		final List<Point> points = new ArrayList<>();
		while(it.hasNext()) {
			final SampledValueDataPoint data = it.next();
			final Point.Builder pb = Point.measurement(measurementId)
					.time(data.getTimestamp(), TimeUnit.MILLISECONDS);
			for (int i=0; i< fieldIds.size(); i++) {
				final SampledValue sv = data.getElement(i);
				if (sv != null) {
					pb.addField(fieldIds.get(i), sv.getValue().getDoubleValue());
					pb.tag("path", paths.get(i)); // FIXME multiple path tags not possible
				}
			}
			points.add(pb.build());
		}
		return points;
	}

	private static String getTagsAsString(final FendoTimeSeries ts) {
		return ts.getProperties().entrySet().stream()
			.map(entry -> entry.getKey() + "=" +entry.getValue().stream().collect(Collectors.joining(",")))
			.collect(Collectors.joining(";"));
	}
	
	@Override
	public String toString() {
		return "FendoDB -> InfluxDB Export [FendoDB: " + config.fendodb() + ", InfluxDB: " + config.url() + ":" + config.influxdb() + "]";
	}
	
	private final String getMeasurementId(final DataRecorderReference ref) {
		String s0 = ref.getPath().toString().replace('\\', '/');
		if (config.fendodb().endsWith("*") && config.fendodb().length() > 1) {
			final String prefix = config.fendodb().substring(0, config.fendodb().indexOf('*'));
			if (s0.startsWith(prefix))
				s0 = s0.substring(prefix.length());
				if (s0.startsWith("/"))
					s0 = s0.substring(1);
		}
		return s0;
	}
	
	private static long parseTimestamp(final String in) {
		try {
			return Long.parseLong(in);
		} catch (NumberFormatException expected) {}
		try {
			return ZonedDateTime.from(DEFAULT_FORMATTER_PARSE_WITH_ZONE.parse(in)).toInstant().toEpochMilli();
		} catch (DateTimeException expected) {}
		try {
			return ZonedDateTime.of(LocalDateTime.from(DEFAULT_FORMATTER_PARSE_NO_ZONE.parse(in)), ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (DateTimeException expected) {
		}
		try {
			return ZonedDateTime.of(LocalDateTime.of(LocalDate.from(DEFAULT_FORMATTER_PARSE_NO_ZONE.parse(in)), LocalTime.MIN), ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (DateTimeException expected) {
		}
		throw new RuntimeException("Unsupported time format " + in);
	}
	
}
