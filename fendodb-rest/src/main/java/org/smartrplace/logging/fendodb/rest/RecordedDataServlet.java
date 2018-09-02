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
package org.smartrplace.logging.fendodb.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ogema.core.administration.FrameworkClock;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.core.recordeddata.RecordedDataConfiguration.StorageType;
import org.ogema.recordeddata.DataRecorderException;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.search.SearchFilterBuilder;
import org.smartrplace.logging.fendodb.stats.StatisticsService;
import org.smartrplace.logging.fendodb.tools.FendoDbTools;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfiguration;
import org.smartrplace.logging.fendodb.tools.config.SerializationConfigurationBuilder;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

// TODO POST value triggers info tasks -> ?
@Component(
	service=Servlet.class,
	property= { 
			HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/*", // prefix to be set in ServletContextHelper
			HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT + "=" + RecordedDataServlet.CONTEXT_FILTER
	}
)
public class RecordedDataServlet extends HttpServlet {

//	private static final Logger logger = LoggerFactory.getLogger(RecordedDataServlet.class);
    private static final long serialVersionUID = 1L;
    public static final String CONTEXT = "org.smartrplace.logging.fendodb.rest";
    public static final String CONTEXT_FILTER = 
    		"(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=" + CONTEXT + ")";

    private final static String[] JSON_FORMATS = {
    	"application/json",
    	"application/x-ldjson",
    	"application/x-json-stream",
    	"application/ld-json",
    	"application/x-ndjson"
    };

    private final static String[] XML_FORMATS = {
    	"application/xml",
    	"text/xml"
    };

    // ok to construct this eagerly, we need it anyway...
    @Reference
    private FendoDbFactory factory;

    // note: accessed reflectively in tests, do not refactor
    @Reference(service=StatisticsService.class)
    private ComponentServiceObjects<StatisticsService> statisticsService;

    @Reference(
    		service=FrameworkClock.class,
    		cardinality=ReferenceCardinality.OPTIONAL,
    		policy=ReferencePolicy.DYNAMIC,
    		policyOption=ReferencePolicyOption.GREEDY
    )
    private volatile ComponentServiceObjects<FrameworkClock> clockService;

    // TODO support multipart?
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	resp.setCharacterEncoding("UTF-8");
    	final String databasePath = req.getParameter(Parameters.PARAM_DB);
    	if (databasePath == null || databasePath.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Database id missing");
    		return;
    	}
    	final String target = req.getParameter(Parameters.PARAM_TARGET);
    	if (target == null || target.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Target missing");
    		return;
    	}
    	final String id = req.getParameter(Parameters.PARAM_ID);
    	final FendodbSerializationFormat format = getFormat(req, false);
    	try (final CloseableDataRecorder recorder = factory.getExistingInstance(Paths.get(databasePath))) {
    		if (recorder == null) {
	    		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Database not found: " + databasePath);
	    		return;
    		}
    		if (recorder.getConfiguration().isReadOnlyMode()) {
    			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Database opened in read-only mode: " + databasePath);
	    		return;
    		}
    		final FendoTimeSeries timeSeries = recorder.getRecordedDataStorage(id);
    		if (timeSeries == null) {
        		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time series " + id + " not found");
        		return;
        	}
    		switch (target.trim().toLowerCase()) {
    		case Parameters.TARGET_VALUE:
    			// {"value":12.3,"time":34}
    			// <entry><value>32.3</value><time>34</time></entry>
    			final ComponentServiceObjects<FrameworkClock> clockService = this.clockService;
    			final FrameworkClock clock = clockService == null ? null : clockService.getService();
    			try {
    				Deserialization.deserializeValue(req.getReader(), timeSeries, format, clock, resp);
    			} finally {
    				if (clock != null)
    					clockService.ungetService(clock);
    			}
    			break;
    		case Parameters.TARGET_VALUES:
    			Deserialization.deserializeValues(req.getReader(), timeSeries, format, resp);
    			break;
            default:
            	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown target " + target);
            	return;
    		}
    	}
    }

    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    	resp.setCharacterEncoding("UTF-8");
    	final String databasePath = req.getParameter(Parameters.PARAM_DB);
    	if (databasePath == null || databasePath.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Database id missing");
    		return;
    	}
    	final String target = req.getParameter(Parameters.PARAM_TARGET);
    	if (target == null || target.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Target missing");
    		return;
    	}
    	if (target.equalsIgnoreCase("database")) {
    		try (final CloseableDataRecorder recorder = factory.getInstance(Paths.get(databasePath))) {
        		if (recorder == null) {
    	    		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Database could not be created: " + databasePath);
    	    		return;
        		}
        	}
    		resp.setStatus(HttpServletResponse.SC_OK);
    		return;
    	}
    	final String id = req.getParameter(Parameters.PARAM_ID);
    	if (id == null)  {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timeseries id missing");
    		return;
    	}
    	try (final CloseableDataRecorder recorder = factory.getExistingInstance(Paths.get(databasePath))) {
    		if (recorder == null) {
	    		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Database not found: " + databasePath);
	    		return;
    		}
    		if (recorder.getConfiguration().isReadOnlyMode()) {
    			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Database opened in read-only mode: " + databasePath);
	    		return;
    		}
    		switch (target.toLowerCase()) {
        	case Parameters.TARGET_TIMESERIES: //create or update timeseries
        		final String updateMode = req.getParameter(Parameters.PARAM_UPDATE_MODE);
            	if (updateMode == null) {
            		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Update mode missing");
            		return;
            	}
            	final StorageType storageType = StorageType.valueOf(updateMode.trim().toUpperCase());
        		final RecordedDataConfiguration config = new RecordedDataConfiguration();
        		config.setStorageType(storageType);
        		if (storageType == StorageType.FIXED_INTERVAL) {
        			final String itv = req.getParameter(Parameters.PARAM_INTERVAL);
        			long interval = 60 * 1000;
        			if (itv != null) {
        				try {
        					interval = Long.parseLong(itv);
        					if (interval <= 0)
        						throw new NumberFormatException();
        				} catch (NumberFormatException e) {
        					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid update interval: " + itv);
        					return;
        				}
        			}
        			config.setFixedInterval(interval);
        		}
            	try {
            		final FendoTimeSeries ts0 = recorder.getRecordedDataStorage(id);
            		if (ts0 != null) {
            			ts0.update(config);
            		} else {
            			recorder.createRecordedDataStorage(id, config);
            		}
            	} catch (DataRecorderException e) {
            		resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            		return;
        		}
            	break;
        	case Parameters.TARGET_PROPERTIES:
        		final String[] properties = req.getParameterValues(Parameters.PARAM_PROPERTIES);
        		if (properties == null || properties.length == 0) {
        			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Properties missing");
        			return;
        		}
        		final FendoTimeSeries slots = recorder.getRecordedDataStorage(id);
        		if (slots == null) {
        			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Timeseries " + id + " not found");
        			return;
        		}
        		Arrays.stream(properties)
					.map(string -> string.split("="))
					.filter(prop -> prop.length == 2)
					.forEach(prop -> {
						final String key = prop[0].trim();
						final String value = prop[1].trim();
						if (key.isEmpty() || value.isEmpty())
							return;
						slots.addProperty(key, value);
					});
        		break;
        	default:
        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown target: " + target);
        		return;
        	}
    	}
    	resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	resp.setCharacterEncoding("UTF-8");
    	final String databasePath = req.getParameter(Parameters.PARAM_DB);
    	if (databasePath == null || databasePath.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Database id missing");
    		return;
    	}
    	final String target = req.getParameter(Parameters.PARAM_TARGET);
    	if (target == null || target.trim().isEmpty()) {
    		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Target missing");
    		return;
    	}
    	try (final CloseableDataRecorder recorder = factory.getExistingInstance(Paths.get(databasePath))) {
    		if (recorder == null) {
	    		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Database not found: " + databasePath);
	    		return;
    		}
        	final String id = req.getParameter(Parameters.PARAM_ID);
        	switch (target.toLowerCase()) {
        	case Parameters.TARGET_PROPERTIES:
        	case Parameters.TARGET_TAG:
        	case Parameters.TARGET_TIMESERIES:
            	if (id == null)  {
            		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timeseries id missing");
            		return;
            	}
	    		final FendoTimeSeries timeSeries = recorder.getRecordedDataStorage(id.trim());
	    		if (timeSeries == null) {
	    			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Timeseries not found: " + id);
		    		return;
	    		}
	    		if (target.equalsIgnoreCase("timeseries")) {
		    		if (!recorder.deleteRecordedDataStorage(id)) {
		    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Delete operation failed for " + id);
		    			return;
		    		}
	    		} else if (target.equalsIgnoreCase("tag")) {
	    			final String tag = req.getParameter(Parameters.PARAM_TAGS);
	    			if (tag == null) {
	    				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tag missing");
			    		return;
	    			}
	    			if (!timeSeries.removeProperty(tag)) {
	    				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Tag " + tag + " not found on timeseries " + id);
	    				return;
	    			}
	    		} else if (target.equalsIgnoreCase("properties")) {
	    			final String[] properties = req.getParameterValues(Parameters.PARAM_PROPERTIES);
	        		if (properties == null || properties.length == 0) {
	        			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Properties missing");
	        			return;
	        		}
	        		final AtomicBoolean anyFound = new AtomicBoolean(false);
	        		Arrays.stream(properties)
						.map(string -> string.split("="))
						.filter(prop -> prop.length == 2)
						.forEach(prop -> {
							final String key = prop[0].trim();
							final String value = prop[1].trim();
							if (key.isEmpty() || value.isEmpty())
								return;
							if (timeSeries.removeProperty(key, value))
								anyFound.set(true);
						});
	        		if (!anyFound.get()) {
	        			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "None of the specified properties found on timeseries " + id);
	    				return;
	        		}
	    		}
	    		break;
    		case Parameters.TARGET_DATA:
	    		final Long start = Utils.parseTimeString(req.getParameter(Parameters.PARAM_START), null);
	    		final Long end = Utils.parseTimeString(req.getParameter(Parameters.PARAM_END), null);
	    		if (start == null && end == null) {
	    			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No start or end time specified");
	    			return;
	    		}
	    		boolean result2 = true;
    			if (start != null)
    				result2 = recorder.deleteDataBefore(Instant.ofEpochMilli(start));
    			if (end != null)
    				result2 = result2 && recorder.deleteDataAfter(Instant.ofEpochMilli(end));
    			if (!result2) {
	    			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Delete operation failed");
	    			return;
    			}
    			break;
        	default:
        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown target: " + target);
        		return;
        	}
			resp.setStatus(HttpServletResponse.SC_OK);
    	}
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    	final String databasePath = req.getParameter(Parameters.PARAM_DB);
    	resp.setCharacterEncoding("UTF-8");
    	 final FendodbSerializationFormat format = getFormat(req, true);
    	if (databasePath == null || databasePath.trim().isEmpty()) {
    		outputDatabaseInstances(resp, getFormat(req, true));
    		setContent(resp, format);
    		resp.setStatus(HttpServletResponse.SC_OK);
        	return;
    	}
    	int idt = 4;
    	final String indent = req.getParameter(Parameters.PARAM_INDENT);
        if (indent != null) {
         	try {
         		idt = Integer.parseInt(indent);
         	} catch (NumberFormatException e) {
         		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a valid indentation: " + indent);
         		return;
         	}
        }
        final char[] indentation = idt >= 0 ? new char[idt] : new char[0];
        Arrays.fill(indentation, ' ');
        final char[] lineBreak = idt >= 0 ? new char[0] : new char[] {'\n'};
        final DateTimeFormatter formatter;
        final String dtFormatter = req.getParameter(Parameters.PARAM_DT_FORMATTER);
        if (dtFormatter != null)
        	formatter = DateTimeFormatter.ofPattern(dtFormatter);
        else
        	formatter = null;
    	String target = req.getParameter(Parameters.PARAM_TARGET);
    	if (target == null)
    		target = Parameters.TARGET_DATA;
    	else
    		target = target.trim().toLowerCase();
    	try (final CloseableDataRecorder recorder = factory.getExistingInstance(Paths.get(databasePath))) {
    		if (recorder == null) {
	    		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Database not found");
	    		return;
    		}
            switch (target) {
            case Parameters.TARGET_DATA:
            	printTimeseriesData(req, resp, recorder, format, formatter);
            	break;
            case Parameters.TARGET_NEXT: // fallthrough
            case Parameters.TARGET_PREVIOUS:
            	final boolean nextOrPrevious = target.equals("nextvalue");
            	final String ida = req.getParameter(Parameters.PARAM_ID);
            	final FendoTimeSeries ts = recorder.getRecordedDataStorage(ida);
            	if (ts == null) {
            		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time series " + ida + " not found");
            		return;
            	}
            	final String timestamp = req.getParameter(Parameters.PARAM_TIMESTAMP);
            	if (timestamp == null) {
            		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timestamp missing");
            		return;
            	}
            	final Long t = Utils.parseTimeString(timestamp, null);
            	if (t == null) {
             		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timestamp missing");
            		return;
            	}
            	final SampledValue sv = nextOrPrevious ? ts.getNextValue(t) : ts.getPreviousValue(t);
            	final String result = Utils.serializeValue(sv, format, formatter, lineBreak, indentation);
            	resp.getWriter().write(result);
            	break;
            case Parameters.TARGET_TAGS:
                final List<FendoTimeSeries> ids;
                final String id0 = req.getParameter(Parameters.PARAM_ID);
                if (id0 != null)
                	ids = Collections.singletonList(recorder.getRecordedDataStorage(id0));
                else
                	ids = recorder.getAllTimeSeries();
            	TagsSerialization.serializeTags(ids, resp.getWriter(), format, indentation, lineBreak);
            	break;
            case Parameters.TARGET_SIZE:
            	final String idb = req.getParameter(Parameters.PARAM_ID);
            	final FendoTimeSeries tsb = recorder.getRecordedDataStorage(idb);
            	if (tsb == null) {
            		resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time series " + idb + " not found");
            		return;
            	}
            	final long start = Utils.parseTimeString(req.getParameter(Parameters.PARAM_START), Long.MIN_VALUE);
                final long end = Utils.parseTimeString(req.getParameter(Parameters.PARAM_END), Long.MAX_VALUE);
                final int size = tsb.size(start, end);
                printSize(resp.getWriter(), idb, lineBreak, indentation, size, format);
                break;
            case Parameters.TARGET_FIND:
            case Parameters.TARGET_STATISTICS:
            	findTimeseries(target, req, resp, recorder, format);
            	break;
            default:
            	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown target " + target);
            	return;
            }
            setContent(resp, format);
    	}
    	resp.setStatus(HttpServletResponse.SC_OK);
    }

    private static void setContent(final HttpServletResponse resp, final FendodbSerializationFormat format) {
    	resp.setContentType(format == FendodbSerializationFormat.XML ? "application/xml" :
        	format == FendodbSerializationFormat.JSON ? "application/json" : "text/csv");
    }

    /**
     * @param target
     * 		either "find" or "stats"
     * @param req
     * @param resp
     * @throws IOException
     */
    private final void findTimeseries(final String target, final HttpServletRequest req, final HttpServletResponse resp,
    		final CloseableDataRecorder recorder, final FendodbSerializationFormat format) throws IOException {
    	final String[] properties = req.getParameterValues(Parameters.PARAM_PROPERTIES);
    	final String[] tags = req.getParameterValues(Parameters.PARAM_TAGS);
    	final String[] ids2 = req.getParameterValues(Parameters.PARAM_ID);
    	final String[] idsExcluded = req.getParameterValues(Parameters.PARAM_ID_EXCLUDED);
    	final SearchFilterBuilder builder = SearchFilterBuilder.getInstance();
//    	System.out.println(" Request params:");
//    	System.out.println("    Props: " + Arrays.toString(properties));
//    	System.out.println("    Tags: " + Arrays.toString(tags));
//    	System.out.println("    Ids: " + Arrays.toString(ids2));
//    	System.out.println("    Excluded: " + Arrays.toString(idsExcluded));

    	if (properties != null) {
    		final Map<String,Collection<String>> map = new HashMap<>(Math.max(4, properties.length));
    		Arrays.stream(properties)
    			.map(string -> string.split("="))
    			.filter(prop -> prop.length == 2)
    			.forEach(prop -> {
    				final String key = prop[0].trim();
    				Collection<String> c = map.get(key);
    				if (c == null) {
    					c = new HashSet<>(2); // typically just one element
    					map.put(key, c);
    				}
    				c.add(prop[1].trim());
    			});
    		builder.filterByPropertiesMultiValues(map, true);
    	}
    	if (tags != null)
    		builder.filterByTags(tags);
    	if (ids2 != null)
    		builder.filterByIncludedIds(Arrays.asList(ids2), true);
    	if (idsExcluded != null)
    		builder.filterByExcludedIds(Arrays.asList(idsExcluded), true);
    	final List<FendoTimeSeries> matches = recorder.findTimeSeries(builder.build());
    	final List<String> ids = matches.stream().map(timeSeries -> timeSeries.getPath()).collect(Collectors.toList());
    	switch (target) {
    	case Parameters.TARGET_FIND:
    		serializeStrings(resp, format, ids, "timeSeries");
    		return;
    	case Parameters.TARGET_STATISTICS:
    		final String[] providers0 = req.getParameterValues(Parameters.PARAM_PROVIDERS);
    		if (providers0 == null || providers0.length == 0) {
    			serializeStrings(resp, format, ids, "timeSeries");
    			return;
    		}
    		final List<String> providerIds = Arrays.asList(providers0);
    		final Long start = Utils.parseTimeString(req.getParameter(Parameters.PARAM_START), null);
    		final Long end = Utils.parseTimeString(req.getParameter(Parameters.PARAM_END), null);
    		final Map<String,?> results;
    		final StatisticsService statistics = statisticsService.getService();
    		try {
	    		if (start == null || end == null)
	    			results = statistics.evaluateByIds(matches, providerIds);
	    		else
	    			results = statistics.evaluateByIds(matches, providerIds, start, end);
    		} finally {
    			statisticsService.ungetService(statistics);
    		}
	    	serializeMap(resp, format, results, "statistics");
    	}
    }

    private static void printSize(final Writer writer, final String id, final char[] lineBreak, final char[] indentation, final int size,
    		final FendodbSerializationFormat format) throws IOException {
    	switch (format) {
        case XML:
        	writer.write("<entry>");
        	writer.write(lineBreak);
        	writer.write(indentation);
        	writer.write("<id>");
        	writer.write(id);
        	writer.write("</id>");
        	writer.write(lineBreak);
        	writer.write(indentation);
        	writer.write("<size>");
        	writer.write(size + "");
        	writer.write("</size>");
        	writer.write(lineBreak);
        	writer.write(indentation);
        	writer.write("</entry>");
        	break;
        case JSON:
        	writer.write('{');
        	writer.write(lineBreak);
        	writer.write(indentation);
        	writer.write("\"id\":\"");
        	writer.write(id);
        	writer.write('\"');
        	writer.write(',');
        	writer.write(lineBreak);
        	writer.write(indentation);
        	writer.write("\"size\":");
        	writer.write(size+ "");
        	writer.write(lineBreak);
        	writer.write('}');
        	break;
        case CSV:
        	writer.write("id:");
        	writer.write(id);
        	writer.write('\n');
        	writer.write("size:");
        	writer.write(size+ "");
    	}
    }

    private static void printTimeseriesData(final HttpServletRequest req, final HttpServletResponse resp,
    		final CloseableDataRecorder recorder, final FendodbSerializationFormat format,
    		final DateTimeFormatter formatter) throws IOException, ServletException {
   		String id = req.getParameter(Parameters.PARAM_ID);
    	if (id == null || id.trim().isEmpty()) {
        	outputRecordedDataIDs(resp, recorder, format);
        	return;
        }
        id = id.trim();
        final FendoTimeSeries ts = recorder.getRecordedDataStorage(id);
        if (ts == null) {
        	resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Timeseries " + id + " does not exist");
        	return;
        }
    	final long start = Utils.parseTimeString(req.getParameter(Parameters.PARAM_START), Long.MIN_VALUE);
        final long end = Utils.parseTimeString(req.getParameter(Parameters.PARAM_END), Long.MAX_VALUE);
        final String samplingIntervalStr = req.getParameter(Parameters.PARAM_INTERVAL);
        final Long samplingInterval;
        try {
        	samplingInterval = samplingIntervalStr == null? null : Long.parseLong(samplingIntervalStr);
        } catch (NumberFormatException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Interval " + samplingIntervalStr + " is not a valid number");
        	return;
        }
        final String maxValuesStr = req.getParameter(Parameters.PARAM_MAX);
        final int maxValues;
        try {
        	maxValues = maxValuesStr == null? 10000 : Integer.parseInt(maxValuesStr);
        } catch (NumberFormatException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid maximum nr argument " + maxValuesStr);
        	return;
        }
        final SerializationConfigurationBuilder builder = SerializationConfigurationBuilder.getInstance()
        		.setInterval(start, end)
        		.setFormat(format)
        		.setFormatter(formatter)
        		.setSamplingInterval(samplingInterval)
        		.setMaxNrValues(maxValues);
        final String indent = req.getParameter(Parameters.PARAM_INDENT);
        if (indent != null) {
        	try {
        		final int i = Integer.parseInt(indent);
        		if (i < 0)
        			builder.setPrettyPrint(false);
        		else
        			builder.setIndentation(i);
        	} catch (NumberFormatException e) {
        		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a valid indentation: " + indent);
        		return;
        	}
        }
        final SerializationConfiguration config = builder.build();
        final int nrDataPoints = FendoDbTools.serialize(resp.getWriter(), ts, config);
        resp.setHeader("nrdatapoints", nrDataPoints + "");
    }

    private void outputDatabaseInstances(final HttpServletResponse resp, final FendodbSerializationFormat format) throws IOException {
    	serializeStrings(resp, format,
    			factory.getAllInstances().keySet().stream()
    				.map(path -> path.toString().replace('\\', '/'))
    				.collect(Collectors.toList()), "database");
    }

    private static void outputRecordedDataIDs(final HttpServletResponse resp, final CloseableDataRecorder recorder,
    		final FendodbSerializationFormat format) throws IOException {
    	serializeStrings(resp, format, recorder.getAllRecordedDataStorageIDs(), "timeSeries");
    }

    private static void serializeStrings(final HttpServletResponse resp, final FendodbSerializationFormat format,
    		final Collection<String> strings, final String entryTag) throws IOException {
    	final PrintWriter writer = resp.getWriter();
    	switch (format) {
    	case XML:
    		writer.println("<entries>");
    		break;
    	case JSON:
    		writer.write("{\"entries\":\n");
    		writer.println('[');
    		break;
    	default:
    	}
    	boolean first = true;
        for (String id : strings) {
        	switch (format) {
        	case XML:
        		writer.write('<');
        		writer.write(entryTag);
        		writer.write('>');
        		writer.write(id);
        		writer.write('<');
        		writer.write('/');
        		writer.write(entryTag);
        		writer.write('>');
        		writer.println();
        		break;
        	case JSON:
        		if (!first) {
        			writer.write(',');
        			writer.println();
        		} else
        			first = false;
        		writer.write('\"');
        		writer.write(id);
        		writer.write('\"');
        		break;
        	default:
        		writer.println(id);
        	}

        }
        switch (format) {
    	case XML:
    		writer.write("</entries>");
    		break;
    	case JSON:
    		writer.println();
    		writer.write(']');
    		writer.write('}');
    		break;
    	default:
    	}
    }

    private static void serializeMap(final HttpServletResponse resp, final FendodbSerializationFormat format,
    		final Map<String, ?> map, final String entryTag) throws IOException {
    	final PrintWriter writer = resp.getWriter();
    	switch (format) {
    	case XML:
    		writer.println("<entries>");
    		break;
    	case JSON:
    		writer.write("{\"entries\":\n");
    		writer.println('{');
    		break;
    	default:
    	}
    	boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
        	final String id = entry.getKey();
        	final Object value = entry.getValue();
        	switch (format) {
        	case XML:
        		writer.write('<');
        		writer.write(entryTag);
        		writer.write('>');
	        		writer.write('<');
	        		writer.write("id");
	        		writer.write('>');
        				writer.write(id);
    				writer.write('<');
	        		writer.write('/');
	        		writer.write("id");
	        		writer.write('>');
	        		writer.write('<');
	        		writer.write("value");
	        		writer.write('>');
	        			writer.write(value.toString());
	        		writer.write('<');
	        		writer.write('/');
	        		writer.write("value");
	        		writer.write('>');
        		writer.write('<');
        		writer.write('/');
        		writer.write(entryTag);
        		writer.write('>');
        		writer.write('\n');
        		break;
        	case JSON:
        		if (!first) {
        			writer.write(',');
        			writer.write('\n');
        		} else
        			first = false;
        		writer.write('\"');
        		writer.write(id);
        		writer.write('\"');
        		writer.write(':');
        		final boolean isNumber = value instanceof Number; 
        		if (!isNumber)
        				writer.write('\"');
        		writer.write((isNumber && Double.isNaN(((Number) value).doubleValue())) ? "null" : value.toString());
        		if (!isNumber)
    				writer.write('\"');
        		break;
        	default:
        		writer.write(id);
        		writer.write(':');
        		writer.write(value.toString());
        		writer.write('\n');
        	}

        }
        switch (format) {
    	case XML:
    		writer.write("</entries>");
    		break;
    	case JSON:
    		writer.println();
    		writer.write('}');
    		writer.write('}');
    		break;
    	default:
    	}
    }

    private static int getJsonIndex(final String header) {
    	return Arrays.stream(JSON_FORMATS)
    		.map(format -> header.indexOf(format))
    		.filter(idx -> idx >= 0)
    		.sorted()
    		.findFirst().orElse(-1);
    }

    private static int getXmlIndex(final String header) {
    	return Arrays.stream(XML_FORMATS)
    		.map(format -> header.indexOf(format))
    		.filter(idx -> idx >= 0)
    		.sorted()
    		.findFirst().orElse(-1);
    }

    private static FendodbSerializationFormat getFormat(final HttpServletRequest req, final boolean acceptOrContentType) {
    	final String format = req.getParameter(Parameters.PARAM_FORMAT);
    	if (format != null) {
   			return FendodbSerializationFormat.valueOf(format.trim().toUpperCase());
    	}
    	final String header = req.getHeader(acceptOrContentType ? "Accept" : "Content-Type");
    	if (header == null)
    		return FendodbSerializationFormat.CSV;
    	final String accept = header.toLowerCase();
        final int returnXML = getXmlIndex(accept);
        final int returnJSON = getJsonIndex(accept);
        final boolean isXml = returnXML != -1 && (returnJSON == -1 || returnXML < returnJSON);
        final boolean isJson = !isXml && returnJSON != -1;
        return isXml ? FendodbSerializationFormat.XML :
         	isJson ? FendodbSerializationFormat.JSON : FendodbSerializationFormat.CSV;
    }

}
