package org.smartrplace.logging.fendodb.influx;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIterator;
import org.ogema.tools.timeseries.iterator.api.MultiTimeSeriesIteratorBuilder;
import org.ogema.tools.timeseries.iterator.api.SampledValueDataPoint;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tagging.api.LogDataTaggingConstants;

/*
 * TODO
 *  - set timeseries path as a tag?
 *  - check last upload, export only newer data
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
		final Map<String, List<FendoTimeSeries>> tsByTags = ts.stream()
			.collect(Collectors.groupingBy(InfluxExport::getTagsAsString));
		return tsByTags.values().stream().mapToLong(tag -> transfer(tag, fendo.getPath().toString().replace('\\', '/'))) // TODO do we need to replace any characters?
			.sum();
	}	
	
	// TODO check which values have been written already, only send new ones!
	private long transfer(final List<FendoTimeSeries> timeSeries, final String fendoId) {
		final BatchPoints.Builder builder = BatchPoints.database(config.influxdb())
				.precision(TimeUnit.MILLISECONDS);
		// Influx DB supports a single tag value only
		timeSeries.iterator().next().getProperties().entrySet().stream()
			.forEach(entry -> builder.tag(entry.getKey(), entry.getValue().iterator().next()));
		final List<String> fieldIds = timeSeries.stream()
				.map(FendoTimeSeries::getProperties)
				.map(InfluxExport::getFieldNameFromProperties)
				.collect(Collectors.toList());
		final List<Point> points;
		if (timeSeries.size() == 1)
			points = getPoints(timeSeries.get(0).iterator(), fendoId, fieldIds.get(0));
		else {
			final MultiTimeSeriesIterator it = 
					MultiTimeSeriesIteratorBuilder.newBuilder(timeSeries.stream().map(ReadOnlyTimeSeries::iterator).collect(Collectors.toList())).build();
			points = getPoints(it, fendoId, fieldIds);
		}
		if (points.isEmpty())
			return 0;
		final Point[] arr = new Point[points.size()];
		points.toArray(arr);
		final BatchPoints batchPoints = BatchPoints.database(config.influxdb())
				.points(arr)
				.build();
			influx.setDatabase(config.influxdb());
			influx.write(batchPoints);
		return arr.length;
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
	
	private static List<Point> getPoints(final Iterator<SampledValue> it, final String fendoId, final String fieldId) {
		final List<Point> points = new ArrayList<>();
		while(it.hasNext()) {
			final SampledValue sv = it.next();
			final Point point = Point.measurement("fendodb_" + fendoId)
					.time(sv.getTimestamp(), TimeUnit.MILLISECONDS)
					.addField(fieldId, sv.getValue().getDoubleValue())
					.build();
			points.add(point);
		}
		return points;
	}
	
	private static List<Point> getPoints(final MultiTimeSeriesIterator it, final String fendoId, final List<String> fieldIds) {
		final List<Point> points = new ArrayList<>();
		while(it.hasNext()) {
			final SampledValueDataPoint data = it.next();
			final Point.Builder pb = Point.measurement("fendodb_" + fendoId)
					.time(data.getTimestamp(), TimeUnit.MILLISECONDS);
			for (int i=0; i< fieldIds.size(); i++) {
				final SampledValue sv = data.getElement(i);
				if (sv != null) {
					pb.addField(fieldIds.get(i), sv.getValue().getDoubleValue());
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

	
}
