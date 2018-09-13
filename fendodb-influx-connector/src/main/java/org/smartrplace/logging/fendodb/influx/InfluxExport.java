package org.smartrplace.logging.fendodb.influx;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.influxdb.InfluxDB;
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
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tagging.api.LogDataTaggingConstants;

/*
 * TODO
 *  - split into multiple measurements?
 */
@Component(
		service = Runnable.class,
		configurationPid = InfluxConfig.PID,
		configurationPolicy = ConfigurationPolicy.REQUIRE,
		// default properties
		property = {
				"org.smartrplace.tools.housekeeping.Period:Long=6",
				"org.smartrplace.tools.housekeeping.Delay:Long=6",
				"org.smartrplace.tools.housekeeping.Unit=HOURS",
				"service.factoryPid=" + InfluxConfig.PID
		}
)
@Designate(ocd=InfluxConfig.class)
// see https://github.com/influxdata/influxdb-java
public class InfluxExport implements Runnable {
	
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
	
	@Activate
	protected void activate(InfluxConfig config) {
		this.config = config;
		this.influx  = InfluxDBFactory.connect(config.url(), config.user(), config.pw());
		influx.setDatabase(config.influxdb());
	}

	@Override
	public void run() {
		final Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("Uploading FendoDb data to InfluxDb");
		if (!influx.ping().isGood()) {
			logger.info("InfluxDb is not reachable");
			return;
		}
		final FendoDbFactory factory = fendoFactory.getService();
		try {
			final CloseableDataRecorder instance = factory.getExistingInstance(Paths.get(config.fendodb()));
			if ( instance == null) {
				logger.warn("Configured FendoDb instance does not exist: {}", config.fendodb());
				return;
			}
			final long size = transfer(instance);
			logger.info("Uploaded {} data points.", size);
		} catch (IOException | InvalidPathException e) {
			logger.warn("Failed to transfer FendoDb data",e);
		} finally {
			fendoFactory.ungetService(factory);
		}
	}
	
	private long transfer(final CloseableDataRecorder fendo) {
		final List<FendoTimeSeries> ts = fendo.getAllTimeSeries();
//		final Map<String, List<FendoTimeSeries>> tsByTags = ts.stream()
//			.collect(Collectors.groupingBy(InfluxExport::getTagsAsString));
//		return tsByTags.values().stream().mapToLong(tag -> transfer(tag, fendo.getPath().toString().replace('\\', '/'))) // TODO do we need to replace any characters?
//			.sum();
		final String fendoId = fendo.getPath().toString().replace('\\', '/');
		return ts.stream()
			.mapToLong(t -> transfer(Collections.singletonList(t), fendoId))
			.sum();
	}	
	
	private long transfer(final List<FendoTimeSeries> timeSeries, final String fendoId) {
		long max = timeSeries.stream()
			.mapToLong(ts -> getLastTimestamp(ts))
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
		try {
			final Field timeField = Point.class.getDeclaredField("time");
			timeField.setAccessible(true);
			while (true) {
				if (max == Long.MAX_VALUE)
					break;
				final List<Point> points;
				if (timeSeries.size() == 1)
					points = getPoints(timeSeries.get(0).getPath(), timeSeries.get(0).iterator(max+1, Long.MAX_VALUE), config.measurementid(), fieldIds.get(0));
				else {
					final long max1 = max;
					final MultiTimeSeriesIterator it = 
							MultiTimeSeriesIteratorBuilder.newBuilder(timeSeries.stream().map(t -> t.iterator(max1+1, Long.MAX_VALUE)).collect(Collectors.toList())).build();
					points = getPoints(timeSeries.stream().map(FendoTimeSeries::getPath).collect(Collectors.toList()), it, config.measurementid(), fieldIds);
				}
				if (points.isEmpty())
					break;
				final Point[] arr = new Point[points.size()];
				points.toArray(arr);
				final BatchPoints batchPoints = builder
						.points(arr)
						.build();
					influx.write(batchPoints);
				size += arr.length; 
				// max = arr[arr.length-1].time
				max = ((Long) timeField.get(arr[arr.length-1])).longValue();
			}
		} catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
		return size;
	}
	
	private long getLastTimestamp(final FendoTimeSeries ts) {
		final String path =  ts.getPath();
		final String fieldId = getFieldNameFromProperties(ts.getProperties());
		final String query = "SELECT last(\"" + fieldId + "\") FROM \"" + config.measurementid() + "\" WHERE \"path\"='" + path +  "'";
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
			LoggerFactory.getLogger(getClass()).warn("Request timed out");
			return null;
		}
		if (error.get() != null) {
			LoggerFactory.getLogger(getClass()).warn("Request failed",error.get());
			return null;
		}
		return results.get();
	}
	
	private static String getFieldNameFromProperties(final Map<String, List<String>> props) {
		final List<String> deviceTypes = props.get(LogDataTaggingConstants.DEVICE_TYPE_SPECIFIC);
		return measurementTypes.stream()
			.filter(type -> containsIgnoreCase(deviceTypes, type))
			.findFirst().orElse("unknown");
	}
	
	private static boolean containsIgnoreCase(final List<String> list, final String value) {
		return list.stream().filter(str -> str.toLowerCase().contains(value)).findAny().isPresent();
	}
	
	private static List<Point> getPoints(final String path, final Iterator<SampledValue> it, final String measurementId, final String fieldId) {
		final List<Point> points = new ArrayList<>();
		int cnt = 0;
		while(it.hasNext() && cnt <= 1000) {
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
	
}
