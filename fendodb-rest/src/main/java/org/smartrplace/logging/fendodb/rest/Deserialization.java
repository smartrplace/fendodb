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
import java.io.Reader;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

import org.ogema.core.administration.FrameworkClock;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.DataRecorderException;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.tools.config.FendodbSerializationFormat;

class Deserialization {
	
	/*
	 * deserialize a stream of xml values:
	 * <entry xsi:type="SampledDouble">
        <time>1519030977043</time>
        <quality>GOOD</quality>
        <value>12.0</value>
       </entry>
	 */
	static boolean deserializeValues(final Reader reader, final FendoTimeSeries timeSeries, final FendodbSerializationFormat format, 
			final HttpServletResponse resp) throws IOException {
		final Deserializer deserializer = format == FendodbSerializationFormat.XML ? new XmlDeserializer(reader, timeSeries, resp) :
				format == FendodbSerializationFormat.JSON ? new JsonDeserializer(reader, timeSeries, resp) :
				new CsvDeserializer(reader, timeSeries, resp);
		final boolean result = deserializer.deserializeValues();
		if (result)
			resp.setStatus(HttpServletResponse.SC_OK);
		return result;
	}
	
	static boolean deserializeValue(final Reader reader, final FendoTimeSeries timeSeries, final FendodbSerializationFormat format, 
			final FrameworkClock clock, final HttpServletResponse resp) throws IOException {
		final char[] arr = new char[256];
		final StringBuilder buffer = new StringBuilder();
		int read = 0;
		int cnt = 0;
		while ((read = reader.read(arr, 0, arr.length)) != -1) {
			buffer.append(arr, 0, read);
			cnt += read;
			if (cnt > 1000) {
				resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
				return false;
			}
		}
		final String request = buffer.toString();
		final String value;
		final Quality q;
		final Long t;
		if (format == FendodbSerializationFormat.XML) {
			final String content = assertExpectedEntryXml(request, 0, "entry", true, resp);
			if (content == null)
				return false;
			t = getOptionalTimestampXml(content, clock, resp);
			if (t == null)
				return false;
			value = assertExpectedEntryXml(content, 0, "value", false, resp);
			if (value == null)
				return false;
			final String quality = assertExpectedEntryXml(content, 0, "quality", false, null);
			q = quality != null && quality.equalsIgnoreCase("bad") ? Quality.BAD : Quality.GOOD;
		} else if (format == FendodbSerializationFormat.JSON) {
			final Map<String,String> map = JsonDeserializer.parseSimpleJsonEntry(request, resp);
			if (map == null)
				return false;
			if (!map.containsKey("value")) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Value field missing");
				return false;
			}
			value = map.get("value");
			if (map.containsKey("time")) {
				t = Utils.parseTimeString(map.get("time"), null);
				if (t == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp " + map.get("time"));
					return false;
				}
			} else
				t = clock != null ? clock.getExecutionTime() : System.currentTimeMillis();
			q = map.containsKey("quality") && map.get("quality").equalsIgnoreCase("bad") ? Quality.BAD : Quality.GOOD;
		}
		else {
			// TODO csv
			final String[] split = request.split(";");
			if (split.length != 2 && split.length != 3) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid CSV request");
				return false;
			}
			t = Utils.parseTimeString(split[0].trim(), null);
			if (t == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp " + split[0]);
				return false;
			}
			value = split[1].trim();
			try {
				q = split.length == 2 ? Quality.GOOD : Quality.valueOf(split[3].trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid quality: " + split[3]);
				return false;
			}
		}
		try {
			final float f = Float.parseFloat(value);
			final SampledValue last = timeSeries.getPreviousValue(Long.MAX_VALUE);
			if (last != null && last.getTimestamp() >= t) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timeseries contains later timestamps");
				return false;
			}
			timeSeries.insertValue(new SampledValue(new FloatValue(f), t, q));
			resp.setStatus(HttpServletResponse.SC_OK);
			return true;
		} catch (NumberFormatException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid value " + value);
			return false;
		} catch (DataRecorderException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not write value to database");
			LoggerFactory.getLogger(RecordedDataServlet.class).error("Error in POST request handling; failed to write value to database, for time series {}",timeSeries,e);
			return false;
		}
	}
	
	private static Long getOptionalTimestampXml(final String target, final FrameworkClock clock, final HttpServletResponse resp) throws IOException {
		final String time = assertExpectedEntryXml(target, 0, "time", false, null);
		final Long t;
		if (time == null)
			t = clock != null ? clock.getExecutionTime() : System.currentTimeMillis();
		else {
			t = Utils.parseTimeString(time, null);
			if (t == null)
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp " + time);
		}
		return t;
	}
	
	/**
	 * @param target
	 * @param startIdx
	 * @param entry
	 * @param strict
	 * 		entry must be next?
	 * @param resp
	 * @return
	 * 		the content of the passed tag
	 * @throws IOException
	 */
	private static String assertExpectedEntryXml(final String target, final int startIdx, final String entry, 
			final boolean strict, final HttpServletResponse resp) throws IOException {
		final int idx = target.indexOf('<', startIdx);
		if (idx < 0) {
			if (resp != null)
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse content");
			return null;
		}
		final int idxClose = target.indexOf('>', idx + 1);
		if (idxClose < 0) {
			if (resp != null)
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse content");
			return null;
		}
		final String tag = getTagName(target, idx, idxClose);
		if (!tag.equalsIgnoreCase(entry)) {
			if (strict) {
				if (resp != null)
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected tag " + tag);
				return null;
			} else {
				return assertExpectedEntryXml(target.substring(idxClose), 0, entry, strict, resp);
			}
		}
		final int tagCloseIdx = target.indexOf("</" + tag + ">", idxClose);
		if (tagCloseIdx < 0) {
			if (resp != null)
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse content");
			return null;
		}
		return target.substring(idxClose+1, tagCloseIdx);
	}
	
	private static String getTagName(final String target, final int idx, final int idxClose) {
		int start = idx + 1;
		while (target.charAt(start) == ' ')
			start++;
		final int emptyIdx = target.indexOf(' ', start);
		final int idxCloseFinal = (emptyIdx < 0 || emptyIdx > idxClose) ? idxClose : emptyIdx;
		return target.substring(idx+1, idxCloseFinal).trim();
	}


}
